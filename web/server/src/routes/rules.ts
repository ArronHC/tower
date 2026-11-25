import { randomUUID } from 'node:crypto';
import { Router } from 'express';
import { z } from 'zod';
import type { AppSnapshot, CustomRuleFlow, RuleScheme, RuleSchemeCustomization, RuleSchemeGroup } from '../../../shared/src/index.js';
import type { AppStateService } from '../store/appStateService.js';
import { activeNodes } from '../services/configGenerator.js';
import { isBuiltinPolicy, parseRuleScheme, ruleLines } from '../services/ruleSchemeParser.js';
import {
  bundledSchemes, detectMatchingOrderConflicts, downloadRules, effectiveScheme, matchingGroupNames, matchingOrderAfterDragging,
  presentGroups, protectedGroupNames, ruleCatalog,
} from '../services/ruleSchemeService.js';
import { fetchText, webURL } from '../services/subscriptionFetcher.js';
import { APIError, invalid, required } from './errors.js';
import { customizationSchema, flowSchema, groupKind, groupSchema, idSchema, localRuleSetSchema, memberSchema, nameList, title, unique } from './schemas.js';

const schemeImport = z.object({ name: title.optional(), url: z.string().optional(), content: z.string().optional() });
const schemeOrder = z.object({ fromIndex: z.number().int().nonnegative(), toIndex: z.number().int().nonnegative(), orderedGroupNames: nameList });
const schemeCustomization = z.object({ customization: customizationSchema, emojiEnabled: z.boolean().optional(), emojiAutofill: z.boolean().optional() });
const groupPatch = z.object({
  newName: title.optional(), kind: groupKind.optional(), members: z.array(memberSchema).optional(),
  nodePattern: z.string().max(2048).optional(), supplementRules: nameList.optional(), isEnabled: z.boolean().optional(),
});
const asRuleURL = (value: string) => {
  try { return webURL(value, true).href; } catch { return invalid('Rules must use HTTPS URLs'); }
};

async function getRuleText(url: string): Promise<string> {
  try { return (await fetchText(webURL(url, true), {}, { httpsOnly: true })).body; }
  catch { throw new APIError(422, 'Could not fetch rule text', 'RULE_DOWNLOAD_FAILED'); }
}

function parseImported(content: string, options: Parameters<typeof parseRuleScheme>[1]): RuleScheme {
  try { return parseRuleScheme(content, options); }
  catch (error) {
    throw new APIError(422, (error as Error).message.includes('HTTPS') ? 'Rules must use HTTPS URLs' : 'Unrecognized rule configuration', 'INVALID_RULE_SCHEME');
  }
}

const ruleURLs = (scheme: RuleScheme) => scheme.rulesets.flatMap(item => item.resource.type === 'remote' ? [item.resource.url] : []);

function schemeGroups(snapshot: AppSnapshot, scheme: RuleScheme) {
  const current = snapshot.ruleSchemeCustomizations[scheme.id];
  return [...scheme.groups, ...current?.addedNodeGroups ?? [], ...snapshot.customRuleFlows.flatMap(flow =>
    flow.schemeID === scheme.id && flow.generatedPolicyGroup ? [flow.generatedPolicyGroup] : [])];
}

function validGroupName(name: string, snapshot: AppSnapshot, scheme: RuleScheme, existing?: string) {
  idSchema.parse(name);
  if (isBuiltinPolicy(name)) invalid('Policy name is reserved');
  const custom = snapshot.ruleSchemeCustomizations[scheme.id];
  if (schemeGroups(snapshot, scheme).some(group => group.name !== existing && !custom?.removedGroupNames?.includes(group.name) &&
      [group.name, custom?.renamedGroups?.[group.name]].includes(name))) throw new APIError(409, 'Policy group name already exists');
}

function changeEnabled(draft: AppSnapshot, id: string, all: string[], name: string, enabled: boolean) {
  const previous = draft.selectedRuleGroups[id] ?? all;
  draft.selectedRuleGroups[id] = enabled ? unique([...previous, name]) : previous.filter(item => item !== name);
}

function parseLocalRules(text: string) {
  try { return ruleLines(text); } catch { return invalid('Invalid rule list'); }
}

function assertGeneration(state: AppStateService, generation: number) {
  if (state.revision !== generation) throw new APIError(409, 'Local state changed during import');
}

export function ruleRoutes(state: AppStateService): Router {
  const routes = Router();
  const view = () => ({ state: state.stateResponse() });
  routes.get('/schemes', (_req, res) => {
    const { bundledSchemes, importedSchemes, activeSchemeGroups, activeSchemeRoutingTargets, activeSchemeAdditionalTargets, snapshot } = state.stateResponse();
    res.json({ bundledSchemes, importedSchemes, selectedPresetID: snapshot.selectedPresetID, activeSchemeGroups, activeSchemeRoutingTargets, activeSchemeAdditionalTargets });
  });
  routes.put('/selected', (req, res) => {
    const { schemeId } = z.object({ schemeId: idSchema }).parse(req.body);
    state.getScheme(schemeId);
    state.update(draft => { draft.selectedPresetID = schemeId; });
    res.json(view());
  });
  routes.post('/schemes/import', async (req, res) => {
    const { name, url, content } = schemeImport.parse(req.body);
    if (!url?.trim() && !content?.trim()) invalid('Provide a rule scheme URL or text');
    const generation = state.revision;
    const sourceURLString = url?.trim() ? asRuleURL(url) : undefined;
    const text = content?.trim() ? content : await getRuleText(sourceURLString!);
    const scheme = parseImported(text, { sourceURLString, name: name ?? (sourceURLString ? new URL(sourceURLString).pathname.split('/').at(-1)?.replace(/\.[^.]+$/, '') : undefined) });
    const downloads = await downloadRules(ruleURLs(scheme));
    assertGeneration(state, generation);
    state.cacheRules(downloads);
    state.update(draft => { draft.importedSchemes.push(scheme); draft.selectedPresetID = scheme.id; });
    res.status(201).json({ scheme, ...view() });
  });
  routes.delete('/schemes/:id', (req, res) => {
    const { id } = req.params;
    if (state.getScheme(id).isBundled) invalid('Bundled rule schemes cannot be deleted');
    state.update(draft => {
      draft.importedSchemes = draft.importedSchemes.filter(item => item.id !== id);
      draft.customRuleFlows = draft.customRuleFlows.filter(flow => flow.schemeID !== id);
      delete draft.ruleSchemeCustomizations[id];
      delete draft.selectedRuleGroups[id];
      delete draft.ruleGroupEmojiAutofill[id];
      delete draft.ruleGroupEmojisEnabled[id];
      if (draft.selectedPresetID === id) draft.selectedPresetID = bundledSchemes[0].id;
    });
    res.json({ ok: true, ...view() });
  });
  routes.post('/schemes/:id/refresh', async (req, res) => {
    const { id } = req.params;
    const old = state.getScheme(id);
    if (old.isBundled || !old.sourceURLString) invalid('This rule scheme has no refresh URL');
    const generation = state.revision;
    const scheme = parseImported(await getRuleText(asRuleURL(old.sourceURLString)), { id, name: old.name, summary: old.summary, sourceURLString: old.sourceURLString });
    const downloads = await downloadRules(ruleURLs(scheme));
    assertGeneration(state, generation);
    if (state.getScheme(id).updatedAt !== old.updatedAt) throw new APIError(409, 'Rule scheme changed during refresh');
    state.cacheRules(downloads);
    state.update(draft => { draft.importedSchemes[draft.importedSchemes.findIndex(item => item.id === id)] = scheme; });
    res.json({ scheme, ...view() });
  });
  routes.put('/schemes/:id/enabled-groups', (req, res) => {
    const { id } = req.params;
    const scheme = state.getScheme(id);
    const { enabledGroupNames } = z.object({ enabledGroupNames: nameList.nullable() }).parse(req.body);
    const allowed = new Set(schemeGroups(state.snapshot, scheme).map(group => group.name));
    state.update(draft => {
      if (enabledGroupNames === null) delete draft.selectedRuleGroups[id];
      else draft.selectedRuleGroups[id] = unique(enabledGroupNames).filter(name => allowed.has(name));
    });
    res.json(view());
  });
  routes.post('/schemes/:id/reorder-groups', (req, res) => {
    const { id } = req.params;
    const scheme = state.getScheme(id);
    const { fromIndex, toIndex, orderedGroupNames } = schemeOrder.parse(req.body);
    const snapshot = state.snapshot;
    const active = activeNodes(snapshot);
    const previous = presentGroups(snapshot, scheme, active, state.downloads).map(group => group.sourceName);
    if (fromIndex >= previous.length || toIndex >= previous.length || previous.length !== orderedGroupNames.length) invalid('Invalid group order');
    const expected = [...previous];
    const [moved] = expected.splice(fromIndex, 1);
    expected.splice(toIndex, 0, moved);
    if (expected.some((name, i) => name !== orderedGroupNames[i])) throw new APIError(409, 'Group order changed. Reload the scheme before moving groups.');
    const previousCustomization = snapshot.ruleSchemeCustomizations[id] ?? null;
    const currentRules = effectiveScheme(snapshot, scheme, state.downloads).rulesets;
    const reverseNames = new Map(Object.entries(previousCustomization?.renamedGroups ?? {}).map(([name, rename]) => [rename, name]));
    const sourceRules = currentRules.map(rule => ({ ...rule, groupName: reverseNames.get(rule.groupName) ?? rule.groupName }));
    const current = matchingGroupNames(sourceRules);
    const old = previousCustomization?.matchingOrder?.filter(name => current.includes(name)) ?? [];
    const previousMatch = old.length ? unique([...old, ...current]) : current;
    const matchingOrder = matchingOrderAfterDragging(moved, previous, expected, previousMatch);
    const conflicts = detectMatchingOrderConflicts(previousMatch, matchingOrder, sourceRules, moved, state.downloads);
    const customization: RuleSchemeCustomization = { ...previousCustomization, groupOrder: expected,
      ...(matchingOrder !== previousMatch ? { matchingOrder } : {}) };
    const updated = state.update(draft => { draft.ruleSchemeCustomizations[id] = customization; });
    res.json({ activeSchemeGroups: presentGroups(updated, scheme, active, state.downloads), customization, previousCustomization, conflicts });
  });
  routes.put('/schemes/:id/customization', (req, res) => {
    const { id } = req.params;
    const scheme = state.getScheme(id);
    const { customization, emojiEnabled, emojiAutofill } = req.body && typeof req.body === 'object' && 'customization' in req.body ?
      schemeCustomization.parse(req.body) : { customization: customizationSchema.parse(req.body), emojiEnabled: undefined, emojiAutofill: undefined };
    const protectedNames = protectedGroupNames(scheme);
    if (customization.removedGroupNames?.some(name => protectedNames.has(name))) invalid('This policy group is required by the rule scheme');
    for (const [original, rename] of Object.entries(customization.renamedGroups ?? {})) validGroupName(rename, state.snapshot, scheme, original);
    if (Object.values(customization.renamedGroups ?? {}).length !== new Set(Object.values(customization.renamedGroups ?? {})).size) invalid('Duplicate policy name');
    state.update(draft => {
      draft.ruleSchemeCustomizations[id] = customization;
      if (emojiEnabled !== undefined) draft.ruleGroupEmojisEnabled[id] = emojiEnabled;
      if (emojiAutofill !== undefined) draft.ruleGroupEmojiAutofill[id] = emojiAutofill;
    });
    res.json(view());
  });
  routes.post('/schemes/:id/groups', (req, res) => {
    const { id } = req.params;
    const scheme = state.getScheme(id);
    const group = groupSchema.parse(req.body);
    validGroupName(group.name, state.snapshot, scheme);
    state.update(draft => {
      const custom = draft.ruleSchemeCustomizations[id] ??= {};
      custom.addedNodeGroups = [...custom.addedNodeGroups ?? [], group];
      custom.removedGroupNames = custom.removedGroupNames?.filter(name => name !== group.name);
      if (draft.selectedRuleGroups[id]) changeEnabled(draft, id, [], group.name, true);
    });
    res.status(201).json(view());
  });
  routes.patch('/schemes/:id/groups/:groupName', (req, res) => {
    const { id, groupName } = req.params;
    const scheme = state.getScheme(id);
    const groups = schemeGroups(state.snapshot, scheme);
    const group = required(groups.find(item => item.name === groupName), 'Policy group not found');
    idSchema.parse(groupName);
    const patch = groupPatch.parse(req.body);
    if (patch.newName) validGroupName(patch.newName, state.snapshot, scheme, groupName);
    const supplemental = patch.supplementRules ? parseLocalRules(patch.supplementRules.join('\n')) : null;
    state.update(draft => {
      const custom = draft.ruleSchemeCustomizations[id] ??= {};
      if (patch.newName) (custom.renamedGroups ??= {})[groupName] = patch.newName;
      if (patch.kind) (custom.groupKindOverrides ??= {})[groupName] = patch.kind;
      const members = patch.members ?? custom.groupMemberOverrides?.[groupName] ?? group.members;
      if (patch.members || patch.nodePattern !== undefined) {
        (custom.groupMemberOverrides ??= {})[groupName] = patch.nodePattern === undefined ? members :
          [...members.filter(item => item.type === 'reference'), ...(patch.nodePattern.trim() ? [{ type: 'nodePattern' as const, pattern: patch.nodePattern.trim() }] : [])];
      }
      if (patch.isEnabled !== undefined) changeEnabled(draft, id, groups.map(item => item.name), groupName, patch.isEnabled);
      if (supplemental) {
        const previous = draft.customRuleFlows.find(flow => flow.schemeID === id && flow.policyName === groupName && flow.isGroupSupplement);
        draft.customRuleFlows = draft.customRuleFlows.filter(flow => !(flow.schemeID === id && flow.policyName === groupName && flow.isGroupSupplement));
        if (supplemental.length) draft.customRuleFlows.push({ id: previous?.id ?? randomUUID(), schemeID: id, policyName: groupName,
          name: groupName, rulesText: supplemental.join('\n'), isEnabled: true, isGroupSupplement: true });
      }
    });
    res.json(view());
  });
  routes.delete('/schemes/:id/groups/:groupName', (req, res) => {
    const { id, groupName } = req.params;
    const scheme = state.getScheme(id);
    const snapshot = state.snapshot;
    const group = required(presentGroups(snapshot, scheme, activeNodes(snapshot), state.downloads).find(group => group.sourceName === groupName), 'Policy group not found');
    if (group.isProtected) invalid('This policy group is required by the rule scheme');
    state.update(draft => {
      const custom = draft.ruleSchemeCustomizations[id] ??= {};
      if (custom.addedNodeGroups?.some(item => item.name === groupName)) {
        custom.addedNodeGroups = custom.addedNodeGroups.filter(item => item.name !== groupName);
      } else custom.removedGroupNames = unique([...custom.removedGroupNames ?? [], groupName]);
      if (custom.renamedGroups) delete custom.renamedGroups[groupName];
      if (custom.groupKindOverrides) delete custom.groupKindOverrides[groupName];
      if (custom.groupMemberOverrides) delete custom.groupMemberOverrides[groupName];
      custom.groupOrder = custom.groupOrder?.filter(name => name !== groupName);
      custom.matchingOrder = custom.matchingOrder?.filter(name => name !== groupName);
      draft.customRuleFlows = draft.customRuleFlows.filter(flow => !(flow.schemeID === id && (flow.policyName === groupName || flow.generatedPolicyGroup?.name === groupName)));
    });
    res.json(view());
  });
  routes.get('/catalog', (req, res) => {
    const { q, source, limit } = z.object({ q: z.string().default(''), source: z.enum(['all', 'acl4ssr', 'blackmatrix7']).default('all'),
      limit: z.coerce.number().int().min(1).max(1000).default(50) }).parse(req.query);
    const query = q.toLowerCase().trim();
    res.json(ruleCatalog.filter(item => (source === 'all' || item.source === source) &&
      (!query || [item.name, item.displayName, item.category, item.source].some(value => value.toLowerCase().includes(query)))).slice(0, limit));
  });
  routes.post('/flows', async (req, res) => {
    const request = flowSchema.omit({ id: true }).parse(req.body);
    const scheme = state.getScheme(request.schemeID);
    const generation = state.revision;
    const catalog = request.catalogID ? required(ruleCatalog.find(item => item.id === request.catalogID), 'Catalog rule not found') : null;
    const local = request.localRuleSetID ? required(state.snapshot.localRuleSets.find(item => item.id === request.localRuleSetID), 'Local rule set not found') : null;
    const url = request.sourceURLString || catalog?.url;
    const flow: CustomRuleFlow = { ...request, id: randomUUID(), sourceURLString: url ? asRuleURL(url) : null };
    let generatedPolicyGroup: RuleSchemeGroup | undefined;
    if (request.generatedPolicyGroup) generatedPolicyGroup = request.generatedPolicyGroup;
    else if (catalog && !isBuiltinPolicy(flow.policyName) && !schemeGroups(state.snapshot, scheme).some(group => group.name === flow.policyName)) {
      const primary = scheme.groups[0]?.name;
      const defaultType = catalog.category === 'advertising' ? 'REJECT' : catalog.category === 'domestic' || catalog.category === 'infrastructure' ? 'DIRECT' : null;
      const names = unique([defaultType, primary, 'DIRECT'].filter((name): name is string => !!name));
      generatedPolicyGroup = { name: flow.policyName, kind: 'select', members: names.map(name => ({ type: 'reference', name })) };
    }
    if (generatedPolicyGroup) {
      validGroupName(generatedPolicyGroup.name, state.snapshot, scheme);
      flow.generatedPolicyGroup = generatedPolicyGroup;
    }
    if (!local && !flow.sourceURLString && !parseLocalRules(flow.rulesText).length) invalid('No rule entries found');
    const downloads = flow.sourceURLString ? await downloadRules([flow.sourceURLString]) : {};
    assertGeneration(state, generation);
    state.getScheme(scheme.id);
    if (generatedPolicyGroup) validGroupName(generatedPolicyGroup.name, state.snapshot, scheme);
    state.cacheRules(downloads);
    state.update(draft => {
      draft.customRuleFlows.push(flow);
      if (generatedPolicyGroup && draft.selectedRuleGroups[scheme.id]) changeEnabled(draft, scheme.id, [], generatedPolicyGroup.name, true);
    });
    res.status(201).json({ flow, ...view() });
  });
  routes.patch('/flows/:id', async (req, res) => {
    const { id } = req.params;
    const patch = flowSchema.omit({ id: true, schemeID: true }).partial().parse(req.body);
    const current = required(state.snapshot.customRuleFlows.find(item => item.id === id), 'Rule flow not found');
    const generation = state.revision;
    const flow = { ...current, ...patch };
    if (patch.sourceURLString) flow.sourceURLString = asRuleURL(patch.sourceURLString);
    if (patch.rulesText !== undefined) parseLocalRules(patch.rulesText);
    const downloads = patch.sourceURLString && flow.sourceURLString ? await downloadRules([flow.sourceURLString]) : {};
    assertGeneration(state, generation);
    const latest = required(state.snapshot.customRuleFlows.find(item => item.id === id), 'Rule flow not found');
    if (JSON.stringify(latest) !== JSON.stringify(current)) throw new APIError(409, 'Rule flow changed during edit');
    state.cacheRules(downloads);
    state.update(draft => { draft.customRuleFlows[draft.customRuleFlows.findIndex(item => item.id === id)] = flow; });
    res.json({ flow, ...view() });
  });
  routes.delete('/flows/:id', (req, res) => {
    const { id } = req.params;
    required(state.snapshot.customRuleFlows.find(item => item.id === id), 'Rule flow not found');
    state.update(draft => { draft.customRuleFlows = draft.customRuleFlows.filter(item => item.id !== id); });
    res.json({ ok: true, ...view() });
  });
  routes.get('/local-sets', (_req, res) => { res.json(state.snapshot.localRuleSets); });
  routes.post('/local-sets', async (req, res) => {
    const request = localRuleSetSchema.omit({ id: true }).parse(req.body);
    const generation = state.revision;
    const sourceURLString = request.sourceURLString ? asRuleURL(request.sourceURLString) : null;
    const rulesText = request.rulesText || (sourceURLString ? await getRuleText(sourceURLString) : '');
    if (!parseLocalRules(rulesText).length) invalid('No rule entries found');
    assertGeneration(state, generation);
    const localRuleSet = { ...request, id: randomUUID(), rulesText, sourceURLString };
    state.update(draft => { draft.localRuleSets.push(localRuleSet); });
    res.status(201).json({ localRuleSet, ...view() });
  });
  routes.patch('/local-sets/:id', (req, res) => {
    const { id } = req.params;
    const current = required(state.snapshot.localRuleSets.find(item => item.id === id), 'Local rule set not found');
    const patch = localRuleSetSchema.omit({ id: true }).partial().parse(req.body);
    if (patch.sourceURLString) patch.sourceURLString = asRuleURL(patch.sourceURLString);
    if (patch.rulesText !== undefined && !parseLocalRules(patch.rulesText).length) invalid('No rule entries found');
    const localRuleSet = { ...current, ...patch };
    state.update(draft => { draft.localRuleSets[draft.localRuleSets.findIndex(item => item.id === id)] = localRuleSet; });
    res.json({ localRuleSet, ...view() });
  });
  routes.delete('/local-sets/:id', (req, res) => {
    const { id } = req.params;
    const current = required(state.snapshot.localRuleSets.find(item => item.id === id), 'Local rule set not found');
    state.update(draft => {
      draft.localRuleSets = draft.localRuleSets.filter(item => item.id !== id);
      draft.customRuleFlows = draft.customRuleFlows.map(flow => flow.localRuleSetID === id ?
        { ...flow, localRuleSetID: null, rulesText: current.rulesText } : flow);
    });
    res.json({ ok: true, ...view() });
  });
  return routes;
}
