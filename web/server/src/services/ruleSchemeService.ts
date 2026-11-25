import { readFileSync } from 'node:fs';
import path from 'node:path';
import type {
  AppSnapshot, MatchingOrderConflict, ProxyNode, RuleCatalogEntry, RuleGroupPresentation, RuleScheme,
  RuleSchemeCustomization, RuleSchemeGroup, RuleSchemeGroupMember, RuleSchemeRuleset, RuleSchemeSummary,
} from '../../../shared/src/index.js';
import { resourcesDir } from '../paths.js';
import { catalogEmoji, leadingEmoji, policyGroupEmoji, policyGroupIconURL, ruleListEmoji, withoutLeadingEmoji } from './policyGroupIcons.js';
import { isBuiltinPolicy, parseRuleScheme, ruleLines, splitRule } from './ruleSchemeParser.js';
import { fetchText, webURL } from './subscriptionFetcher.js';

type PublishedSet = { url: string; residualRuleCount?: number; noResolve?: boolean; coveredRuleTypes?: string[] };
interface ACLManifest {
  configs: Record<string, { file: string; name: string; summary: string; source: string }>;
  rulesets: Record<string, { source: string; surge?: PublishedSet; loon?: PublishedSet; quanx?: PublishedSet; egern?: PublishedSet;
    mrs?: Record<string, PublishedSet>; srs?: PublishedSet; srsDNS?: PublishedSet; mrsResidualRuleCount?: number }>;
  inlineRules: Record<string, Record<string, PublishedSet>>;
  quanxCatchAll: PublishedSet;
}
const aclDirectory = path.join(resourcesDir, 'ACL4SSR');
const aclRead = (file: string) => readFileSync(path.join(aclDirectory, file), 'utf8');
export const aclManifest: ACLManifest = JSON.parse(aclRead('ACL4SSR_manifest.json'));

const byURL = new Map(Object.entries(aclManifest.rulesets).map(([file, item]) => [item.source, file]));
const bundledLines = new Map(Object.keys(aclManifest.rulesets).map(file => [file, ruleLines(aclRead(file))]));
export const bundledSchemes = Object.entries(aclManifest.configs).map(([id, config]) => {
  const scheme = parseRuleScheme(aclRead(config.file), { id, name: config.name, summary: config.summary, isBundled: true });
  scheme.rulesets = scheme.rulesets.map(item => {
    if (item.resource.type !== 'remote') return item;
    const file = byURL.get(item.resource.url);
    if (!file) throw new Error('ACL4SSR preset references a missing bundled ruleset');
    return { ...item, resource: { type: 'bundled', file } };
  });
  return scheme;
});

export function rulesetLines(item: RuleSchemeRuleset, downloads: Readonly<Record<string, string>> = {}): string[] {
  if (item.resource.type === 'inline') return [item.resource.rule];
  if (item.resource.type === 'bundled') return bundledLines.get(item.resource.file) ?? [];
  const content = Object.hasOwn(downloads, item.resource.url) ? downloads[item.resource.url] : undefined;
  return content ? ruleLines(content) : [];
}

export function findScheme(snapshot: AppSnapshot, id = snapshot.selectedPresetID): RuleScheme | undefined {
  return [...bundledSchemes, ...snapshot.importedSchemes].find(item => item.id === id);
}

const unique = <T>(values: T[]) => [...new Set(values)];
const isFinal = ({ resource }: RuleSchemeRuleset) => resource.type === 'inline' && /^(?:FINAL|MATCH)$/i.test(resource.rule);
const isGeo = ({ resource }: RuleSchemeRuleset) => resource.type === 'inline' && /^GEOIP,/i.test(resource.rule);
const isMovable = (rule: RuleSchemeRuleset) => rule.origin !== 'supplement' && !isFinal(rule) && !isGeo(rule);
export const matchingGroupNames = (rules: RuleSchemeRuleset[]): string[] => unique(rules
  .filter(rule => rule.origin !== 'supplement' && !isFinal(rule) && !isBuiltinPolicy(rule.groupName)).map(rule => rule.groupName));

export function protectedGroupNames(source: RuleScheme): Set<string> {
  const fixed = new Set(['全球直连', '全球拦截', '漏网之鱼', 'global direct', 'global reject', 'final', 'final match']);
  return new Set([...source.rulesets.filter(isFinal).map(rule => rule.groupName),
    ...source.groups.filter(group => fixed.has(withoutLeadingEmoji(group.name).toLowerCase())).map(group => group.name)]);
}

export function matchingOrderAfterDragging(name: string, previousDisplay: string[], displayOrder: string[], current: string[]): string[] {
  const position = current.indexOf(name);
  const from = previousDisplay.indexOf(name);
  const to = displayOrder.indexOf(name);
  if (position < 0 || from < 0 || to < 0 || from === to) return current;
  const down = from < to;
  const passed = displayOrder.slice(down ? from : to + 1, down ? to : from + 1);
  const candidates = passed.map(group => current.indexOf(group)).filter(index => index >= 0 && (down ? index > position : index < position));
  if (!candidates.length) return current;
  const anchor = current[down ? Math.max(...candidates) : Math.min(...candidates)];
  const next = current.filter(group => group !== name);
  next.splice(next.indexOf(anchor) + Number(down), 0, name);
  return next;
}

function longestIncreasingSubsequence(values: number[]): number[] {
  const tails: number[] = [];
  const previous: number[] = [];
  for (const [index, value] of values.entries()) {
    let low = 0, high = tails.length;
    while (low < high) {
      const middle = Math.floor((low + high) / 2);
      if (values[tails[middle]] < value) low = middle + 1;
      else high = middle;
    }
    previous[index] = tails[low - 1] ?? -1;
    tails[low] = index;
  }
  const result: number[] = [];
  for (let index = tails.at(-1) ?? -1; index >= 0; index = previous[index]) result.push(index);
  return result.reverse();
}

export function reordering(rulesets: RuleSchemeRuleset[], order: string[], draggedGroup?: string): RuleSchemeRuleset[] {
  const supplements = rulesets.filter(rule => rule.origin === 'supplement');
  const rules = rulesets.filter(rule => rule.origin !== 'supplement');
  const rank = new Map(matchingGroupNames(rules).map((name, index) => [name, index]));
  const desired = unique(order).filter(name => rank.has(name));
  if (desired.length < 2) return [...supplements, ...rules];
  const indices = longestIncreasingSubsequence(desired.map(name => rank.get(name)!));
  const retained = new Set(indices.map(i => desired[i]));
  if (draggedGroup && indices.length === desired.length - 1) {
    const rest = desired.filter(name => name !== draggedGroup);
    if (rest.length === desired.length - 1 && rest.every((name, i) => !i || rank.get(rest[i - 1])! < rank.get(name)!)) {
      retained.clear();
      rest.forEach(name => retained.add(name));
    }
  }
  const moved = new Set(desired.filter(name => !retained.has(name)));
  const lifts = (rule: RuleSchemeRuleset) => isMovable(rule) && moved.has(rule.groupName);
  const pending = new Map<string, RuleSchemeRuleset[]>();
  const trailing: RuleSchemeRuleset[] = [];
  desired.forEach((name, index) => {
    if (!moved.has(name)) return;
    const block = rules.filter(rule => rule.groupName === name && lifts(rule));
    const anchor = desired.slice(index + 1).find(candidate => retained.has(candidate) && rules.some(rule => isMovable(rule) && rule.groupName === candidate));
    if (anchor) pending.set(anchor, [...pending.get(anchor) ?? [], ...block]);
    else trailing.push(...block);
  });
  const result: RuleSchemeRuleset[] = [...supplements];
  for (const rule of rules.filter(rule => !lifts(rule))) {
    if (isMovable(rule) && pending.has(rule.groupName)) {
      result.push(...pending.get(rule.groupName)!);
      pending.delete(rule.groupName);
    }
    result.push(rule);
  }
  if (trailing.length) {
    const lastKept = desired.findLast(name => retained.has(name) && result.some(rule => rule.groupName === name && isMovable(rule)));
    const anchor = result.findLastIndex(rule => rule.groupName === lastKept && isMovable(rule));
    const fallback = result.findIndex(rule => isGeo(rule) || isFinal(rule));
    result.splice(anchor >= 0 ? anchor + 1 : fallback >= 0 ? fallback : result.length, 0, ...trailing);
  }
  return result;
}

const wildcard = (value: string) => new RegExp('^' + value.split(/([*?])/).map(segment =>
  segment === '*' ? '.*' : segment === '?' ? '.' : segment.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('') + '$', 'i');

function domainMatcher(lines: string[]) {
  const exact = new Set<string>(), suffix = new Set<string>(), keywords = new Set<string>(), patterns: RegExp[] = [];
  const samples = new Set<string>();
  for (const line of lines) {
    const parsed = splitRule(line);
    if (!parsed) continue;
    const value = parsed.value.toLowerCase();
    switch (parsed.type) {
      case 'HOST':
      case 'DOMAIN': exact.add(value); samples.add(value); break;
      case 'HOST-SUFFIX':
      case 'DOMAIN-SUFFIX': suffix.add(value); samples.add(value); samples.add('www.' + value); break;
      case 'HOST-KEYWORD':
      case 'DOMAIN-KEYWORD': keywords.add(value); samples.add(value.includes('.') ? value : value + '.example'); break;
      case 'DOMAIN-WILDCARD':
        patterns.push(wildcard(value));
        samples.add(value.replace(/\*|\?/g, 'www'));
        break;
    }
  }
  return {
    samples,
    test: (value: string) => {
      if (exact.has(value)) return true;
      const parts = value.split('.');
      if (parts.some((_, index) => suffix.has(parts.slice(index).join('.')))) return true;
      return [...keywords].some(keyword => value.includes(keyword)) || patterns.some(pattern => pattern.test(value));
    },
  };
}

export function detectMatchingOrderConflicts(
  oldOrder: string[], newOrder: string[], rules: RuleSchemeRuleset[], movedGroup?: string,
  downloads: Readonly<Record<string, string>> = {},
): MatchingOrderConflict[] {
  const matchers = new Map<string, ReturnType<typeof domainMatcher>>();
  const forGroup = (group: string) => {
    const existing = matchers.get(group);
    if (existing) return existing;
    const value = domainMatcher(rules.filter(rule => rule.groupName === group && isMovable(rule)).flatMap(rule => rulesetLines(rule, downloads)));
    matchers.set(group, value);
    return value;
  };
  const conflicts: MatchingOrderConflict[] = [];
  oldOrder.forEach((before, index) => {
    const a = newOrder.indexOf(before);
    if (a < 0) return;
    for (const after of oldOrder.slice(index + 1)) {
      const b = newOrder.indexOf(after);
      if (b < 0 || b >= a || (movedGroup && movedGroup !== before && movedGroup !== after)) continue;
      const first = forGroup(before), second = forGroup(after);
      const sampleDomains: string[] = [];
      for (const domain of unique([...first.samples, ...second.samples])) {
        if (!first.test(domain) || !second.test(domain)) continue;
        // A third group that already won the same domain masks this swap.
        const earlier = oldOrder.slice(0, index).some(name => newOrder.indexOf(name) >= 0 && newOrder.indexOf(name) < b && forGroup(name).test(domain));
        if (!earlier) sampleDomains.push(domain);
        if (sampleDomains.length >= 5) break;
      }
      if (sampleDomains.length) conflicts.push({ movedGroup: movedGroup ?? after, otherGroup: movedGroup === before ? after : before,
        sampleDomains, direction: movedGroup === before ? 'nowShadowedByOther' : 'shadowedByMoved' });
    }
  });
  return conflicts;
}

export function effectiveScheme(snapshot: AppSnapshot, source: RuleScheme, downloads: Readonly<Record<string, string>> = {}, match = true): RuleScheme {
  const customization = snapshot.ruleSchemeCustomizations[source.id] ?? {};
  const flows = snapshot.customRuleFlows.filter(flow => flow.schemeID === source.id && flow.isEnabled);
  const groups = uniqueGroups([...source.groups, ...flows.flatMap(flow => flow.generatedPolicyGroup ? [flow.generatedPolicyGroup] : []), ...customization.addedNodeGroups ?? []]);
  const protectedNames = protectedGroupNames(source);
  const removed = new Set(customization.removedGroupNames?.filter(name => !protectedNames.has(name)) ?? []);
  const renamed = (name: string) => customization.renamedGroups?.[name] || name;
  const enabled = snapshot.selectedRuleGroups[source.id];
  const selected = new Set([...enabled ?? groups.map(group => group.name), ...protectedNames]);
  const groupLookup = new Map(groups.map(group => [group.name, group]));
  for (const name of selected) {
    const group = groupLookup.get(name);
    if (!group || removed.has(name)) continue;
    for (const member of customization.groupMemberOverrides?.[name] ?? group.members) {
      if (member.type === 'reference') selected.add(member.name);
    }
  }
  const active = (name: string) => !removed.has(name) && (!enabled || selected.has(name) || isBuiltinPolicy(name));
  const sourceRules = source.rulesets.filter(rule => active(rule.groupName) || isFinal(rule));
  const supplements: RuleSchemeRuleset[] = [];
  const rules = [...sourceRules];
  const claims = new Set<string>();
  for (const flow of flows) {
    if (!active(flow.policyName)) continue;
    const local = snapshot.localRuleSets.find(item => item.id === flow.localRuleSetID);
    const text = local?.rulesText || flow.rulesText;
    const url = flow.sourceURLString;
    const additions: RuleSchemeRuleset[] = url && !flow.isGroupSupplement ? [{
      groupName: flow.policyName, resource: { type: 'remote', url }, origin: 'added', addedLabel: flow.name,
    }] : ruleLines(text).flatMap(line => {
      if (flow.isGroupSupplement && !/^(?:DOMAIN(?:-SUFFIX|-KEYWORD|-WILDCARD)?|IP-CIDR6?),/i.test(line)) return [];
      if (flow.isGroupSupplement && claims.has(line.toLowerCase())) return [];
      if (flow.isGroupSupplement) claims.add(line.toLowerCase());
      return [{ groupName: flow.policyName, resource: { type: 'inline', rule: line },
        origin: flow.isGroupSupplement ? 'supplement' : 'added', addedLabel: flow.name }];
    });
    if (flow.isGroupSupplement) supplements.push(...additions);
    else if (additions.length) {
      const matcher = domainMatcher(additions.flatMap(rule => rulesetLines(rule, downloads)));
      let at = rules.findIndex(rule => {
        const other = domainMatcher(rulesetLines(rule, downloads));
        return [...matcher.samples].some(domain => other.test(domain)) || [...other.samples].some(domain => matcher.test(domain));
      });
      if (at < 0) at = rules.findIndex(rule => isGeo(rule) || isFinal(rule));
      rules.splice(at < 0 ? rules.length : at, 0, ...additions);
    }
  }
  const priority = (rule: RuleSchemeRuleset) => {
    if (rule.resource.type !== 'inline') return 0;
    const parsed = splitRule(rule.resource.rule);
    if (!parsed) return 0;
    if (parsed.type === 'DOMAIN') return 1000 + parsed.value.length;
    if (/^IP-CIDR6?$/.test(parsed.type)) return 750 + Number(parsed.value.split('/')[1] || 0);
    if (parsed.type === 'DOMAIN-SUFFIX') return 500 + parsed.value.length;
    return parsed.type === 'DOMAIN-WILDCARD' ? 250 + parsed.value.length : parsed.value.length;
  };
  supplements.sort((a, b) => priority(b) - priority(a));
  const ordered = match ? reordering(rules, customization.matchingOrder ?? []) : rules;
  return {
    ...source, networkSettings: customization.overridesNetworkSettings ? customization.networkSettingsOverride : source.networkSettings,
    groups: orderGroups(groups, customization).filter(group => active(group.name)).map(group => ({
      ...group, name: renamed(group.name), kind: customization.groupKindOverrides?.[group.name] ?? group.kind,
      members: (customization.groupMemberOverrides?.[group.name] ?? group.members).flatMap<RuleSchemeGroupMember>(member =>
        member.type === 'nodePattern' ? [member] : removed.has(member.name) ? [] : [{ ...member, name: renamed(member.name) }]),
    })),
    rulesets: [...supplements, ...ordered].map(rule => ({ ...rule, groupName: renamed(rule.groupName) })),
  };
}

function uniqueGroups(groups: RuleSchemeGroup[]) {
  const seen = new Set<string>();
  return groups.filter(group => !seen.has(group.name) && seen.add(group.name));
}

function orderGroups(groups: RuleSchemeGroup[], customization: RuleSchemeCustomization) {
  const positions = new Map((customization.groupOrder ?? []).map((name, index) => [name, index]));
  return [...groups].sort((a, b) => (positions.get(a.name) ?? Infinity) - (positions.get(b.name) ?? Infinity));
}

export function nodeMatchesPattern(name: string, pattern: string): boolean {
  try {
    const insensitive = pattern.startsWith('(?i)');
    return new RegExp(insensitive ? pattern.slice(4) : pattern, insensitive ? 'i' : '').test(name);
  } catch { return false; }
}

export function presentGroups(snapshot: AppSnapshot, source: RuleScheme, nodes: ProxyNode[], downloads: Readonly<Record<string, string>> = {}): RuleGroupPresentation[] {
  const customization = snapshot.ruleSchemeCustomizations[source.id] ?? {};
  const flows = snapshot.customRuleFlows.filter(flow => flow.schemeID === source.id);
  const groups = uniqueGroups([...source.groups, ...flows.flatMap(flow => flow.generatedPolicyGroup ? [flow.generatedPolicyGroup] : []), ...customization.addedNodeGroups ?? []]);
  const enabled = snapshot.selectedRuleGroups[source.id];
  const effective = effectiveScheme(snapshot, source, downloads);
  const activeNames = new Set(effective.groups.map(group => group.name));
  const protectedNames = protectedGroupNames(source);
  return orderGroups(groups, customization).filter(group => protectedNames.has(group.name) || !customization.removedGroupNames?.includes(group.name)).map(group => {
    const name = customization.renamedGroups?.[group.name] || group.name;
    const members = customization.groupMemberOverrides?.[group.name] ?? group.members;
    const kind = customization.groupKindOverrides?.[group.name] ?? group.kind;
    const patterns = members.flatMap(member => member.type === 'nodePattern' ? [member.pattern] : []);
    const isNodeFilterGroup = !!group.isCustomNodeFilter || (patterns.length > 0 && patterns.every(pattern => pattern !== '.*'));
    const emoji = policyGroupEmoji({ ...group, kind, name, members }, effective.rulesets,
      snapshot.ruleGroupEmojiAutofill[source.id] ?? !source.isBundled);
    const emojiEnabled = snapshot.ruleGroupEmojisEnabled[source.id] !== false;
    const sourceRules = source.rulesets.filter(rule => rule.groupName === group.name);
    const effectiveRules = effective.rulesets.filter(rule => rule.groupName === name);
    const isProtected = protectedNames.has(group.name) || (enabled != null && !enabled.includes(group.name) && activeNames.has(name));
    return {
      sourceName: group.name, displayName: withoutLeadingEmoji(name) || name, emoji: emojiEnabled ? emoji : null,
      iconURL: emojiEnabled ? policyGroupIconURL(emoji) : null, kind, members, isProtected,
      isEnabled: activeNames.has(name),
      isNodeFilterGroup, ruleCount: (effectiveRules.length ? effectiveRules : sourceRules).reduce((count, rule) => count + Math.max(1, rulesetLines(rule, downloads).length), 0),
      supplementRules: flows.filter(flow => flow.policyName === group.name && flow.isGroupSupplement).flatMap(flow => ruleLines(flow.rulesText)),
      matchedNodeCount: patterns.length ? nodes.filter(node => patterns.some(pattern => nodeMatchesPattern(node.name, pattern))).length : 0,
      editorMode: isNodeFilterGroup ? 'nodePatternsOnly' : 'routingTargets',
    };
  });
}

export function schemeSummary(snapshot: AppSnapshot, scheme: RuleScheme): RuleSchemeSummary {
  const { id, name, summary, isBundled, sourceURLString, updatedAt } = scheme;
  return {
    id, name, summary, isBundled, sourceURLString, updatedAt,
    groupCount: scheme.groups.length, rulesetCount: scheme.rulesets.length,
    emojiEnabled: snapshot.ruleGroupEmojisEnabled[id] !== false, emojiAutofill: snapshot.ruleGroupEmojiAutofill[id] ?? !isBundled,
  };
}

const catalogCategory: Record<string, string> = {
  '🤖': 'ai', '🔷': 'ai', '🎥': 'streaming', '📹': 'streaming', '🎬': 'streaming', '🧸': 'streaming',
  '📺': 'streaming', '🎵': 'streaming', '🎧': 'streaming', '🍿': 'streaming', '💬': 'social', '📲': 'social',
  '🐦': 'social', '♾️': 'social', '🎮': 'gaming', '🛑': 'advertising', '🍃': 'advertising',
  '🎯': 'domestic', '🏠': 'infrastructure', '🌐': 'infrastructure', '🍎': 'infrastructure', 'Ⓜ️': 'infrastructure',
};
const catalogFallback: Record<string, string> = { ai: '✨', streaming: '🎬', social: '💬', gaming: '🎮', advertising: '🛑',
  domestic: '🎯', infrastructure: '🌐', other: '🧩' };

export const ruleCatalog = (['acl4ssr', 'blackmatrix7'] as const).flatMap(source => {
  const file = source === 'acl4ssr' ? 'ACL4SSR.list-index.txt' : 'blackmatrix7.list-index.txt';
  const origin = source === 'acl4ssr' ? 'ACL4SSR/ACL4SSR' : 'blackmatrix7/ios_rule_script';
  return readFileSync(path.join(resourcesDir, 'RuleCatalog', file), 'utf8').split(/\r?\n/).filter(line => line.endsWith('.list'))
    .map((relative): RuleCatalogEntry => {
      const name = path.basename(relative, '.list');
      const category = catalogCategory[ruleListEmoji(name) ?? ''] ?? 'other';
      const emoji = catalogEmoji(name, catalogFallback[category]);
      return { id: `${source}:${relative}`, source, name, displayName: leadingEmoji(name) ? name : `${emoji} ${name}`,
        category, emoji, url: `https://raw.githubusercontent.com/${origin}/master/${relative.split('/').map(encodeURIComponent).join('/')}` };
    });
});

export async function downloadRules(urls: string[]): Promise<Record<string, string>> {
  const results: Record<string, string> = {};
  const buckets = new Map<string, URL[]>();
  for (const raw of unique(urls)) {
    const url = webURL(raw, true);
    const imports = buckets.get(url.hostname) ?? [];
    imports.push(url);
    buckets.set(url.hostname, imports);
  }
  await Promise.all([...buckets.values()].map(async imports => {
    for (const url of imports) {
      if (/\.(?:mrs|srs)$/i.test(url.pathname)) continue;
      try {
        const { body } = await fetchText(url, {}, { httpsOnly: true });
        if (ruleLines(body).length) results[url.href] = body;
      } catch { /* Keep the remote reference for clients that can load it. */ }
    }
  }));
  return results;
}
