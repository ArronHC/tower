import { randomUUID } from 'node:crypto';
import { load, JSON_SCHEMA } from 'js-yaml';
import type { RuleScheme, RuleSchemeGroup, RuleSchemeGroupKind, RuleSchemeGroupMember, RuleSchemeNetworkSettings, RuleSchemeRuleset } from '../../../shared/src/index.js';
import { cleanLine, iniSections, list, record, splitFields, str } from './subscriptionParser.js';
import { webURL } from './subscriptionFetcher.js';

export const groupKinds = ['select', 'url-test', 'fallback', 'load-balance', 'smart', 'ssid', 'relay', 'unsupported'] as const;
const builtin = new Set('DIRECT REJECT REJECT-DROP REJECT-NO-DROP REJECT-TINYGIF REJECT-IMG PASS PASS-RULE COMPATIBLE CELLULAR CELLULAR-ONLY HYBRID NO-HYBRID'.split(' '));
export const isBuiltinPolicy = (name: string) => builtin.has(name.toUpperCase());
const normalizeKind = (name: string): RuleSchemeGroupKind => {
  const normalized = ({ static: 'select', selector: 'select', 'url-latency-benchmark': 'url-test', auto_test: 'url-test', urltest: 'url-test',
    available: 'fallback', loadbalance: 'load-balance', load_balance: 'load-balance', 'round-robin': 'load-balance',
    'dest-hash': 'load-balance', conditional: 'ssid', subnet: 'ssid' } as Record<string, RuleSchemeGroupKind>)[name.toLowerCase()] ?? name.toLowerCase();
  return groupKinds.includes(normalized as RuleSchemeGroupKind) ? normalized as RuleSchemeGroupKind : 'unsupported';
};

const positive = (value: unknown) => Number.isFinite(Number(value)) && Number(value) > 0 ? Number(value) : undefined;
const member = (name: string): RuleSchemeGroupMember => ({ type: 'reference', name });
const nodePattern = (pattern: string): RuleSchemeGroupMember => ({ type: 'nodePattern', pattern });

export function splitRule(line: string): { type: string; value: string; options: string[] } | null {
  const fields = splitFields(line);
  const type = fields.shift()?.toUpperCase();
  if (type === 'FINAL' || type === 'MATCH') return { type: 'FINAL', value: '', options: fields };
  const value = fields.shift();
  if (!type || !/^[A-Z][A-Z0-9-]*$/.test(type) || !value || /[\r\n\u0085\u2028\u2029]/.test(line)) return null;
  return { type, value, options: fields.filter(Boolean) };
}

export function ruleLines(text: string): string[] {
  if (/^\s*payload\s*:/m.test(text)) {
    const yaml = record(load(text, { schema: JSON_SCHEMA }));
    return Array.isArray(yaml.payload) ? yaml.payload.filter((line): line is string => typeof line === 'string').flatMap(normalizeRuleLine) : [];
  }
  return text.split(/\r?\n/).flatMap(normalizeRuleLine);
}

function normalizeRuleLine(raw: string): string[] {
  const line = raw.trim();
  if (!line || /^(?:#|;|\/\/|\[)/.test(line)) return [];
  if (line.includes(',')) return [line];
  if (/^\+?\*?\.[-\w.]+$/.test(line)) return [line.startsWith('*.') ? 'DOMAIN-WILDCARD,' + line : 'DOMAIN-SUFFIX,' + line.replace(/^\+?\./, '')];
  if (/^[\da-f:.]+\/\d+$/i.test(line)) return [`${line.includes(':') ? 'IP-CIDR6' : 'IP-CIDR'},${line}`];
  if (/^[\w-]+(?:\.[\w-]+)+$/.test(line)) return ['DOMAIN,' + line];
  return [];
}

function parseRule(line: string, providers: Map<string, string>): RuleSchemeRuleset | null {
  const parsed = splitRule(line);
  if (!parsed) return null;
  let { type, value } = parsed;
  const [groupName, ...options] = parsed.options;
  if (!groupName) return null;
  if (type === 'RULE-SET' || type === 'DOMAIN-SET') {
    const pointer = providers.get(value) ?? value;
    if (/^https?:/i.test(pointer)) return { groupName, resource: { type: 'remote', url: webURL(pointer, true).href }, options };
  }
  type = ({ 'HOST': 'DOMAIN', 'HOST-SUFFIX': 'DOMAIN-SUFFIX', 'HOST-KEYWORD': 'DOMAIN-KEYWORD', 'IP6-CIDR': 'IP-CIDR6' } as Record<string, string>)[type] ?? type;
  return { groupName, resource: { type: 'inline', rule: value ? `${type},${value}` : type }, options };
}

function expandRule(line: string, remote: Map<string, string>, local: Map<string, string[]>): RuleSchemeRuleset[] {
  const parsed = splitRule(line);
  if (parsed?.type === 'RULE-SET' || parsed?.type === 'DOMAIN-SET') {
    const [groupName, ...options] = parsed.options;
    const lines = local.get(parsed.value);
    if (lines && groupName) return lines.map(rule => ({ groupName, resource: { type: 'inline', rule }, options }));
  }
  const rule = parseRule(line, remote);
  return rule ? [rule] : [];
}

function keyValues(lines: string[]) {
  return Object.fromEntries(lines.flatMap(line => {
    const match = /^([^=]+)=(.*)$/.exec(line);
    return match ? [[match[1].trim().toLowerCase(), match[2].trim()]] : [];
  }));
}

function networkForINI(general: Record<string, string>): RuleSchemeNetworkSettings {
  return {
    dnsServers: list(general['dns-server']), fallbackDNSServers: list(general['encrypted-dns-server']),
    skipProxyHosts: list(general['skip-proxy']), tunExcludedRoutes: list(general['tun-excluded-routes']),
    ipv6: general.ipv6 == null ? undefined : general.ipv6.toLowerCase() === 'true',
  };
}

function parseINI(text: string) {
  const sections = iniSections(text);
  const groups: RuleSchemeGroup[] = [];
  const rulesets: RuleSchemeRuleset[] = [];
  const local = new Map([...sections].flatMap(([name, lines]) => name.startsWith('ruleset ') ? [[name.slice(8), ruleLines(lines.join('\n'))]] : []));
  const networkSettings = networkForINI(keyValues(sections.get('general') ?? []));
  const custom = [...sections.get('custom') ?? [], ...sections.get('') ?? []];
  for (const line of custom) {
    const match = /^(ruleset|custom_proxy_group)\s*=\s*(.*)$/i.exec(line);
    if (!match) continue;
    if (match[1].toLowerCase() === 'ruleset') {
      const i = match[2].indexOf(',');
      if (i < 1) continue;
      const groupName = match[2].slice(0, i).trim();
      const resource = match[2].slice(i + 1).trim();
      if (resource.startsWith('[]')) {
        const [type, value, ...options] = splitFields(resource.slice(2));
        rulesets.push({ groupName, resource: { type: 'inline', rule: /^MATCH$/i.test(type) ? 'FINAL' : [type, value].filter(Boolean).join(',') }, options });
      } else if (/^https?:/i.test(resource)) {
        rulesets.push({ groupName, resource: { type: 'remote', url: webURL(resource, true).href } });
      } else throw new Error('Only HTTPS ruleset URLs are supported');
    } else {
      const [name, type, ...parts] = match[2].split('`');
      if (!name || !type) continue;
      const endpoint = parts.findIndex(part => /^https?:\/\//i.test(part));
      const [interval, , tolerance] = (parts[endpoint + 1] ?? '').split(',');
      groups.push({ name: cleanLine(name), kind: normalizeKind(type),
        members: (endpoint < 0 ? parts : parts.slice(0, endpoint)).filter(Boolean)
          .map(part => part.startsWith('[]') ? member(part.slice(2)) : nodePattern(part)),
        testURL: endpoint < 0 ? undefined : parts[endpoint], interval: positive(interval), tolerance: positive(tolerance) });
    }
  }
  for (const line of sections.get('proxy group') ?? []) {
    const found = /^([^=]+)=(.*)$/.exec(line);
    if (!found) continue;
    const [type, ...values] = splitFields(found[2]);
    const parameters = keyValues(values);
    const members = values.filter(value => value && !value.includes('=')).map(member);
    if (/^(true|1)$/i.test(parameters['include-all-proxies'] ?? '') || parameters['policy-path']) members.push(nodePattern('.*'));
    if (parameters['policy-regex-filter']) {
      const pattern = parameters['policy-regex-filter'];
      const position = members.findIndex(item => item.type === 'nodePattern');
      if (position >= 0) members.splice(position, 1);
      members.push(nodePattern(pattern));
    }
    groups.push({ name: cleanLine(found[1]), kind: normalizeKind(type), members,
      testURL: parameters.url, interval: positive(parameters.interval), tolerance: positive(parameters.tolerance) });
  }
  for (const line of sections.get('proxy chain') ?? []) {
    const found = /^([^=]+)=(.*)$/.exec(line);
    if (found) groups.push({ name: cleanLine(found[1]), kind: 'relay', members: splitFields(found[2]).map(member) });
  }
  for (const line of sections.get('rule') ?? []) {
    const parsed = splitRule(line);
    if (parsed?.type === 'RULE-SET' && local.has(parsed.value.toLowerCase())) {
      rulesets.push(...expandRule(line, new Map(), new Map([[parsed.value, local.get(parsed.value.toLowerCase())!]])));
    } else rulesets.push(...expandRule(line, new Map(), local));
  }
  for (const line of sections.get('remote rule') ?? []) {
    const [url, ...fields] = splitFields(line);
    const settings = keyValues(fields);
    const groupName = settings.policy ?? fields.find(field => !field.includes('='));
    if (url && groupName && settings.enabled?.toLowerCase() !== 'false') {
      const index = rulesets.findIndex(rule => rule.resource.type === 'inline' && /^FINAL$/i.test(rule.resource.rule));
      rulesets.splice(index < 0 ? rulesets.length : index, 0, { groupName, resource: { type: 'remote', url: webURL(url, true).href } });
    }
  }
  return { groups, rulesets, networkSettings };
}

function parseYAML(text: string) {
  const yaml = record(load(text, { schema: JSON_SCHEMA }));
  const dns = record(yaml.dns);
  const providerEntries = Object.entries(record(yaml['rule-providers']));
  const providers = new Map(providerEntries.flatMap(([key, value]) => {
    const url = str(record(value).url);
    return url ? [[key, webURL(url, true).href]] : [];
  }));
  const local = new Map(providerEntries.flatMap(([key, value]) => {
    const { payload } = record(value);
    return Array.isArray(payload) ? [[key, payload.filter((line): line is string => typeof line === 'string').flatMap(normalizeRuleLine)]] : [];
  }));
  const groups: RuleSchemeGroup[] = [];
  for (const value of Array.isArray(yaml['proxy-groups']) ? yaml['proxy-groups'] : []) {
    const p = record(value);
    if (typeof p.name !== 'string') continue;
    const members = list(p.proxies).map(member);
    const pattern = str(p.filter) ?? (p['include-all'] || p['include-all-proxies'] || p['include-all-providers'] || list(p.use).length ? '.*' : null);
    if (pattern) members.push(nodePattern(pattern));
    groups.push({ name: cleanLine(p.name), kind: normalizeKind(String(p.type ?? 'select')), members,
      iconURL: str(p.icon), testURL: str(p.url), interval: positive(p.interval), tolerance: positive(p.tolerance) });
  }
  const rulesets = (Array.isArray(yaml.rules) ? yaml.rules : []).flatMap(line => typeof line === 'string' ? expandRule(line, providers, local) : []);
  const networkSettings: RuleSchemeNetworkSettings = {
    dnsServers: list(dns.nameserver), fallbackDNSServers: list(dns.fallback),
    directDNSServers: list(dns['direct-nameserver']), tunExcludedRoutes: list(record(yaml.tun)['route-exclude-address']),
    ipv6: typeof dns.ipv6 === 'boolean' ? dns.ipv6 : undefined,
  };
  return { groups, rulesets, networkSettings };
}

export function parseRuleScheme(text: string, options: { id?: string; name?: string; summary?: string; sourceURLString?: string; isBundled?: boolean } = {}): RuleScheme {
  const parsed = /^\s*(?:proxy-groups|rule-providers|rules|proxies)\s*:/m.test(text) ? parseYAML(text) : parseINI(text);
  if (!parsed.groups.length && !parsed.rulesets.length) throw new Error('No policy groups or rules found');
  const names = new Set<string>();
  parsed.groups = parsed.groups.filter(group => group.name && group.name.length <= 256 && !isBuiltinPolicy(group.name) &&
    !['__proto__', 'prototype', 'constructor'].includes(group.name) && !names.has(group.name) && names.add(group.name));
  return {
    id: options.id ?? randomUUID(), name: options.name?.trim().slice(0, 256) || '导入的规则方案', summary: options.summary ?? '',
    isBundled: options.isBundled ?? false, sourceURLString: options.sourceURLString,
    rawConfigurationText: options.isBundled ? undefined : text,
    updatedAt: options.isBundled ? undefined : new Date().toISOString(),
    ...parsed,
  };
}
