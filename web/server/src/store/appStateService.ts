import { randomUUID } from 'node:crypto';
import { networkInterfaces } from 'node:os';
import { DEFAULT_APP_SETTINGS, DEFAULT_CLIENT_ORDER } from '../../../shared/src/index.js';
import type {
  AppSnapshot, AppStateResponse, LANSharingStatus, NodeLatencyMeasurement, NodeLatencyTestMode, NodeRuntimeInfo, ProxyNode,
  RuleScheme, SubscriptionMutationResult, SubscriptionSource, TailnetConnection,
} from '../../../shared/src/index.js';
import { APIError, invalid, required } from '../routes/errors.js';
import { snapshotSchema, unique } from '../routes/schemas.js';
import { activeNodes } from '../services/configGenerator.js';
import { ipDatabase, ipBytes } from '../services/ipCountryDb.js';
import { mapConcurrent, probeNode } from '../services/nodeLatencyService.js';
import { countries, nodeRegion, NodeRegionResolver } from '../services/nodeRegionResolver.js';
import type { HostInfo } from '../services/nodeRegionResolver.js';
import { bundledSchemes, findScheme, presentGroups, schemeSummary } from '../services/ruleSchemeService.js';
import { fetchSubscription, webURL } from '../services/subscriptionFetcher.js';
import { canonicalKey, isSubscriptionMetadata, parseSubscription, validateNode } from '../services/subscriptionParser.js';
import { normalizeTailnet, tailnetStableSlug } from '../services/tailnetWriter.js';
import { newLANToken, PersistenceStore, publicSnapshot } from './persistenceStore.js';
import type { PersistedLAN } from './persistenceStore.js';

export function initialSnapshot(): AppSnapshot {
  return {
    version: 1, updatedAt: new Date().toISOString(), subscriptions: [], nodes: [], selectedPresetID: 'acl4ssr-default',
    selectedTarget: 'shadowrocket', importedSchemes: [], selectedRuleGroups: {}, ruleSchemeCustomizations: {},
    ruleGroupEmojisEnabled: {}, ruleGroupEmojiAutofill: {}, excludedNodeIDs: [], nodeExportNameFilter: null,
    customRuleFlows: [], localRuleSets: [], excludedKinds: {}, clientOrder: [...DEFAULT_CLIENT_ORDER],
    visibleClientTargets: [...DEFAULT_CLIENT_ORDER], lanSharingOrderIndex: 0, isLANSharingVisible: true, exportContentModes: {},
    tailnets: [], settings: { ...DEFAULT_APP_SETTINGS },
  };
}

function normalizedSnapshot(input: unknown): AppSnapshot {
  const fresh = initialSnapshot();
  if (!input || typeof input !== 'object' || Array.isArray(input)) invalid('Invalid backup');
  const value = input as Partial<AppSnapshot>;
  if (!Number.isInteger(value.version) || !Array.isArray(value.subscriptions) || !Array.isArray(value.nodes)) invalid('Invalid backup');
  const parsed = snapshotSchema.parse({ ...fresh, ...value, settings: { ...fresh.settings, ...value.settings } });
  const snapshot = publicSnapshot(parsed);
  snapshot.tailnets = snapshot.tailnets.map(normalizeTailnet);
  for (const objects of [snapshot.subscriptions, snapshot.nodes, snapshot.importedSchemes, snapshot.customRuleFlows, snapshot.localRuleSets, snapshot.tailnets]) {
    if (new Set(objects.map(object => object.id)).size !== objects.length) invalid('Duplicate identifier in backup');
  }
  const sourceIDs = new Set(snapshot.subscriptions.map(source => source.id));
  for (const source of snapshot.subscriptions) normalizeSource(source);
  snapshot.nodes = snapshot.nodes.filter(node => !node.sourceID || sourceIDs.has(node.sourceID));
  snapshot.excludedNodeIDs = unique(snapshot.excludedNodeIDs).filter(id => snapshot.nodes.some(node => node.id === id));
  snapshot.clientOrder = unique([...snapshot.clientOrder, ...DEFAULT_CLIENT_ORDER]);
  snapshot.visibleClientTargets = unique(snapshot.visibleClientTargets);
  snapshot.importedSchemes = snapshot.importedSchemes.filter(scheme => !scheme.isBundled && !bundledSchemes.some(bundled => bundled.id === scheme.id));
  if (!findScheme(snapshot)) snapshot.selectedPresetID = fresh.selectedPresetID;
  return snapshot;
}

function groupByKey(nodes: ProxyNode[], includeName: boolean) {
  const groups = new Map<string, ProxyNode[]>();
  for (const node of nodes) {
    const key = includeName ? canonicalKey(node) : stableNodeKey(node);
    const matches = groups.get(key) ?? [];
    matches.push(node);
    groups.set(key, matches);
  }
  return groups;
}

function stableNodeKey(node: ProxyNode): string {
  return JSON.stringify([node.kind, (node.hostsSourceServer || node.server).toLowerCase(), node.port,
    node.password ?? '', node.uuid ?? '', node.username ?? '', node.cipher ?? '']);
}

export function carriedOverExclusions(previous: ProxyNode[], previouslyExcludedIDs: Iterable<string>, refreshed: ProxyNode[]) {
  const excluded = new Set(previouslyExcludedIDs);
  const exact = groupByKey(previous, true);
  const exactUpdated = groupByKey(refreshed, true);
  const loose = groupByKey(previous, false);
  const looseUpdated = groupByKey(refreshed, false);
  const excludedNodeIDs: string[] = [];
  const nodes = refreshed.map(node => {
    const exactKey = canonicalKey(node);
    const looseKey = stableNodeKey(node);
    const exactMatches = exact.get(exactKey) ?? [];
    const oldLoose = loose.get(looseKey) ?? [];
    const looseMatch = oldLoose.length === 1 && looseUpdated.get(looseKey)?.length === 1 ? oldLoose[0] : null;
    const match = exactMatches.length === 1 && exactUpdated.get(exactKey)?.length === 1 ? exactMatches[0] : exactMatches.length ? null : looseMatch;
    if (exactMatches.some(old => excluded.has(old.id)) || (!exactMatches.length && looseMatch && excluded.has(looseMatch.id))) {
      excludedNodeIDs.push(node.id);
    }
    return match?.countryOverride ? { ...node, countryOverride: match.countryOverride } : node;
  });
  return { nodes, excludedNodeIDs };
}

function normalizeSource(source: SubscriptionSource) {
  try {
    source.urlString = webURL(source.urlString).href;
    if (source.requestOptions?.dnsOverHTTPSURL) source.requestOptions.dnsOverHTTPSURL = webURL(source.requestOptions.dnsOverHTTPSURL, true).href;
  } catch (error) { invalid((error as Error).message); }
}

type Fetcher = typeof fetchSubscription;

export interface StateOptions {
  store?: PersistenceStore;
  port?: number;
  host?: string;
  fetcher?: Fetcher;
  resolver?: NodeRegionResolver;
}

export class AppStateService {
  private state: AppSnapshot;
  private lan: PersistedLAN;
  private keys: Record<string, string>;
  private remoteRules: Record<string, string>;
  private readonly store: PersistenceStore;
  private readonly fetcher: Fetcher;
  private readonly resolver: NodeRegionResolver;
  private readonly host: string;
  private port: number;
  private readonly runtime = new Map<string, { network?: HostInfo; latency?: NodeLatencyMeasurement }>();
  private readonly hostQueue = new Map<string, Promise<unknown>>();
  private readonly sourceRefreshes = new Map<string, Promise<SubscriptionMutationResult>>();
  private readonly sourceTickets = new Map<string, symbol>();
  private generation = 0;

  constructor(options: StateOptions = {}) {
    this.store = options.store ?? new PersistenceStore();
    this.port = options.port ?? 38420;
    this.host = options.host ?? '0.0.0.0';
    this.fetcher = options.fetcher ?? fetchSubscription;
    this.resolver = options.resolver ?? new NodeRegionResolver();
    const persisted = this.store.readState();
    const envelope = persisted && 'snapshot' in persisted ? persisted : null;
    this.state = persisted ? normalizedSnapshot(envelope ? envelope.snapshot : persisted) : initialSnapshot();
    this.lan = envelope?.lan && /^[A-Za-z0-9_-]{24,128}$/.test(envelope.lan.token)
      ? { enabled: Boolean(envelope.lan.enabled), token: envelope.lan.token } : { enabled: false, token: newLANToken() };
    const storedKeys = this.store.readKeys();
    this.keys = Object.fromEntries(this.state.tailnets.flatMap(tailnet => {
      const key = Object.hasOwn(storedKeys, tailnet.id) ? storedKeys[tailnet.id] : undefined;
      return typeof key === 'string' && /^[\w-]+$/.test(key) ? [[tailnet.id, key]] : [];
    }));
    if (Object.keys(this.keys).length !== Object.keys(storedKeys).length) this.store.writeKeys(this.keys);
    this.remoteRules = this.store.readRules();
    if (!envelope || this.lan.token !== envelope.lan?.token) this.store.writeState({ snapshot: this.state, lan: this.lan });
    this.scheduleGeo(this.state.nodes.filter(node => !isSubscriptionMetadata(node)));
  }

  get snapshot(): AppSnapshot { return this.state; }
  get authKeys(): Readonly<Record<string, string>> { return this.keys; }
  get downloads(): Readonly<Record<string, string>> { return this.remoteRules; }
  get revision(): number { return this.generation; }
  get listenPort(): number { return this.port; }
  setListenPort(port: number) { this.port = port; }
  get lanStatus(): LANSharingStatus {
    const addresses = !['0.0.0.0', '::'].includes(this.host) ? [this.host] :
      [...Object.values(networkInterfaces()).flatMap(list => list ?? []).filter(item => item.family === 'IPv4' && !item.internal).map(item => item.address), '127.0.0.1'];
    const urls = unique(addresses).map(host => `http://${host.includes(':') ? '[' + host + ']' : host}:${this.port}/sub/${this.lan.token}`);
    return { ...this.lan, port: this.port, urls, primaryURL: urls[0] };
  }

  setLAN(enabled: boolean, rotate = false): LANSharingStatus {
    const lan = { enabled, token: rotate ? newLANToken() : this.lan.token };
    this.store.writeState({ snapshot: this.state, lan });
    this.lan = lan;
    return this.lanStatus;
  }

  update(edit: (draft: AppSnapshot) => void): AppSnapshot {
    const draft = structuredClone(this.state);
    edit(draft);
    draft.updatedAt = new Date().toISOString();
    this.store.writeState({ snapshot: draft, lan: this.lan });
    this.state = draft;
    if (this.runtime.size) {
      const live = new Set(draft.nodes.map(node => node.id));
      for (const id of this.runtime.keys()) if (!live.has(id)) this.runtime.delete(id);
    }
    return draft;
  }

  cacheRules(downloads: Record<string, string>) {
    if (!Object.keys(downloads).length) return;
    const rules = { ...this.remoteRules, ...downloads };
    this.store.writeRules(rules);
    this.remoteRules = rules;
  }

  replace(input: unknown, reset = false) {
    const snapshot = reset ? initialSnapshot() : normalizedSnapshot(input);
    const prior = this.keys;
    const keys = reset ? {} : Object.fromEntries(snapshot.tailnets.flatMap(tailnet => Object.hasOwn(prior, tailnet.id) ? [[tailnet.id, prior[tailnet.id]]] : []));
    const lan = reset ? { enabled: false, token: newLANToken() } : this.lan;
    this.store.writeKeys(keys);
    try { this.store.writeState({ snapshot, lan }); }
    catch (error) { this.store.writeKeys(prior); throw error; }
    this.state = snapshot;
    this.keys = keys;
    this.lan = lan;
    this.generation++;
    this.runtime.clear();
    this.sourceTickets.clear();
    this.sourceRefreshes.clear();
    if (reset) {
      this.store.writeRules({});
      this.remoteRules = {};
    }
    this.scheduleGeo(snapshot.nodes.filter(node => !isSubscriptionMetadata(node)));
  }

  getTailnets(): TailnetConnection[] {
    return this.state.tailnets.map(item => ({
      ...item, stableSlug: tailnetStableSlug(item.id), hasAuthKey: Object.hasOwn(this.keys, item.id),
    }));
  }

  updateTailnet(connection: TailnetConnection, authKey?: string | null, remove = false) {
    let normalized: TailnetConnection;
    try { normalized = normalizeTailnet(connection); } catch (error) { invalid((error as Error).message); }
    const oldKeys = this.keys;
    const keys = { ...oldKeys };
    if (remove || authKey === null) delete keys[connection.id];
    else if (authKey !== undefined) keys[connection.id] = authKey;
    if (remove || authKey !== undefined) this.store.writeKeys(keys);
    try {
      this.update(draft => {
        const index = draft.tailnets.findIndex(item => item.id === connection.id);
        if (remove) draft.tailnets.splice(index, 1);
        else if (index >= 0) draft.tailnets[index] = normalized;
        else draft.tailnets.push(normalized);
      });
    } catch (error) {
      if (remove || authKey !== undefined) this.store.writeKeys(oldKeys);
      throw error;
    }
    this.keys = keys;
    return this.getTailnets().find(item => item.id === connection.id);
  }

  private runtimeInfo(node: ProxyNode): NodeRuntimeInfo {
    const current = this.runtime.get(node.id);
    const db = !current?.network && ipBytes(node.server) ? ipDatabase() : null;
    const organization = db?.organization(node.server);
    const network = current?.network ?? { country: db?.countryCode(node.server) ?? null,
      resolvedIP: db ? node.server : null, asn: organization?.asn ?? null, asnOrg: organization?.name ?? null };
    const { country, ...hostInfo } = network;
    return {
      ...nodeRegion(node, country), ...hostInfo, latency: current?.latency,
    };
  }

  private get runtimeRecord(): Record<string, NodeRuntimeInfo> {
    return Object.fromEntries(this.state.nodes.map(node => [node.id, this.runtimeInfo(node)]));
  }

  stateResponse(): AppStateResponse {
    const snapshot = { ...publicSnapshot(this.state), tailnets: this.getTailnets() };
    const scheme = findScheme(snapshot) ?? bundledSchemes[0];
    const nodes = activeNodes(snapshot).filter(node => !snapshot.settings.filterSubscriptionInfoNodes || !isSubscriptionMetadata(node));
    const runtime = this.runtimeRecord;
    const activeSchemeGroups = presentGroups(snapshot, scheme, nodes, this.downloads);
    const names = activeSchemeGroups.map(group => group.displayName);
    const defaults = unique(['DIRECT', 'REJECT', ...activeSchemeGroups.filter(group => group.isProtected || group.isNodeFilterGroup).map(group => group.sourceName)]);
    return {
      snapshot, runtime,
      bundledSchemes: bundledSchemes.map(scheme => schemeSummary(snapshot, scheme)),
      importedSchemes: snapshot.importedSchemes.map(scheme => schemeSummary(snapshot, scheme)),
      activeSchemeGroups, activeSchemeRoutingTargets: defaults,
      activeSchemeAdditionalTargets: Object.fromEntries(activeSchemeGroups.map((group, index) => [
        group.sourceName, unique(activeSchemeGroups.filter((other, i) => i !== index && !defaults.includes(other.sourceName) && names[i]).map(other => other.sourceName)),
      ])),
      lanStatus: this.lanStatus,
      stats: {
        subscriptionCount: snapshot.subscriptions.length,
        enabledSubscriptionCount: snapshot.subscriptions.filter(source => source.isEnabled).length,
        totalNodeCount: snapshot.nodes.filter(node => !snapshot.settings.filterSubscriptionInfoNodes || !isSubscriptionMetadata(node)).length,
        activeNodeCount: nodes.length,
        countryCount: new Set(nodes.map(node => runtime[node.id]?.countryCode).filter(Boolean)).size,
      },
    };
  }

  private scheduleGeo(nodes: ProxyNode[]) {
    void this.updateGeo(nodes).catch(() => undefined);
  }

  async resolveGeo(ids?: string[]) {
    const requested = ids ? new Set(ids) : null;
    const nodes = this.state.nodes.filter(node => !requested || requested.has(node.id));
    await this.updateGeo(nodes);
    return this.runtimeRecord;
  }

  private async updateGeo(nodes: ProxyNode[]) {
    const generation = this.generation;
    await mapConcurrent(nodes, 24, async node => {
      const network = await this.resolver.resolveNetwork(node.server);
      const current = this.state.nodes.find(item => item.id === node.id);
      if (generation !== this.generation || !current || current.server !== node.server) return;
      this.runtime.set(node.id, { ...this.runtime.get(node.id), network });
    });
  }

  async latency(ids: string[] | undefined, mode: NodeLatencyTestMode) {
    const generation = this.generation;
    const requested = ids ? new Set(ids) : null;
    const nodes = this.state.nodes.filter(node => (requested ? requested.has(node.id) :
      (!node.sourceID || this.state.subscriptions.some(source => source.id === node.sourceID && source.isEnabled)) && !isSubscriptionMetadata(node)));
    const results = await mapConcurrent(nodes, 16, async node => {
      const value = await probeNode(node, mode);
      const current = this.state.nodes.find(item => item.id === node.id);
      const stored = this.runtime.get(node.id);
      if (generation === this.generation && current?.server === node.server && current.port === node.port &&
          (!stored?.latency || stored.latency.testedAt <= value.testedAt)) {
        this.runtime.set(node.id, { ...stored, latency: value });
      }
      return [node.id, value] as const;
    });
    return Object.fromEntries(results);
  }

  private queueFetch(source: SubscriptionSource) {
    const host = webURL(source.urlString).hostname;
    const previous = this.hostQueue.get(host) ?? Promise.resolve();
    const task = previous.catch(() => undefined).then(() => this.fetcher(source.urlString, source.requestOptions ?? {}, source.id));
    this.hostQueue.set(host, task);
    return task.finally(() => { if (this.hostQueue.get(host) === task) this.hostQueue.delete(host); });
  }

  private response(source: SubscriptionSource, result: Awaited<ReturnType<Fetcher>>): SubscriptionMutationResult {
    return {
      subscription: source, addedNodeCount: result.nodes.filter(node => !this.state.settings.filterSubscriptionInfoNodes || !isSubscriptionMetadata(node)).length,
      skippedCount: result.skippedCount, skippedReasons: result.skippedReasons, state: this.stateResponse(),
    };
  }

  async addSubscription(input: Pick<SubscriptionSource, 'urlString' | 'requestOptions'> & { name?: string }) {
    const generation = this.generation;
    const source: SubscriptionSource = {
      id: randomUUID(), name: input.name?.trim() ?? '', urlString: input.urlString,
      requestOptions: input.requestOptions ?? null, createdAt: new Date().toISOString(), isEnabled: true,
      nameWasAutoGenerated: !input.name?.trim(),
    };
    normalizeSource(source);
    if (this.state.subscriptions.some(existing => existing.urlString === source.urlString)) throw new APIError(409, 'Subscription already exists');
    const result = await this.loadSubscription(source);
    if (generation !== this.generation) throw new APIError(409, 'Local state changed during import');
    if (this.state.subscriptions.some(existing => existing.urlString === source.urlString)) throw new APIError(409, 'Subscription already exists');
    source.name ||= result.name || new URL(source.urlString).hostname.replace(/^www\./, '');
    Object.assign(source, { lastUpdatedAt: new Date().toISOString(), lastError: null, usage: result.usage });
    this.update(draft => { draft.subscriptions.push(source); draft.nodes.push(...result.nodes); });
    this.scheduleGeo(result.nodes.filter(node => !isSubscriptionMetadata(node)));
    return this.response(source, result);
  }

  async patchSubscription(id: string, patch: Partial<Pick<SubscriptionSource, 'name' | 'urlString' | 'isEnabled' | 'requestOptions'>>) {
    const source = required(this.state.subscriptions.find(item => item.id === id), 'Subscription not found');
    const changed = { ...source, ...patch, requestOptions: structuredClone(patch.requestOptions !== undefined ? patch.requestOptions : source.requestOptions) };
    normalizeSource(changed);
    const requestChanged = source.urlString !== changed.urlString || JSON.stringify(source.requestOptions) !== JSON.stringify(changed.requestOptions);
    if (patch.name !== undefined) changed.nameWasAutoGenerated = false;
    if (requestChanged) {
      if (this.state.subscriptions.some(item => item.id !== id && item.urlString === changed.urlString)) throw new APIError(409, 'Subscription already exists');
      const ticket = Symbol();
      this.sourceTickets.set(id, ticket);
      this.sourceRefreshes.delete(id);
      const result = await this.loadSubscription(changed);
      const current = this.currentSource(id, ticket);
      if (this.state.subscriptions.some(item => item.id !== id && item.urlString === changed.urlString)) throw new APIError(409, 'Subscription already exists');
      const updated = this.commitRefresh({ ...current, ...patch, urlString: changed.urlString, requestOptions: changed.requestOptions,
        nameWasAutoGenerated: patch.name === undefined ? current.nameWasAutoGenerated : false }, result);
      return { subscription: updated, state: this.stateResponse() };
    }
    this.update(draft => { draft.subscriptions[draft.subscriptions.findIndex(item => item.id === id)] = changed; });
    return { subscription: changed, state: this.stateResponse() };
  }

  removeSubscription(id: string) {
    required(this.state.subscriptions.find(item => item.id === id), 'Subscription not found');
    this.update(draft => {
      const removed = new Set(draft.nodes.filter(node => node.sourceID === id).map(node => node.id));
      draft.subscriptions = draft.subscriptions.filter(item => item.id !== id);
      draft.nodes = draft.nodes.filter(node => !removed.has(node.id));
      draft.excludedNodeIDs = draft.excludedNodeIDs.filter(node => !removed.has(node));
    });
    this.sourceTickets.delete(id);
    this.sourceRefreshes.delete(id);
  }

  private async loadSubscription(source: SubscriptionSource) {
    try { return await this.queueFetch(source); }
    catch (error) {
      const result = error as Error & { skippedCount?: number; skippedReasons?: unknown };
      const message = /^(?:Server returned HTTP \d+|No supported nodes found|Too many redirects|Response body is too large)$/.test(result.message)
        ? result.message : 'Could not fetch subscription';
      throw new APIError(422, message, 'SUBSCRIPTION_FETCH_FAILED',
        result.skippedCount == null ? undefined : { skippedCount: result.skippedCount, skippedReasons: result.skippedReasons });
    }
  }

  private currentSource(id: string, ticket: symbol): SubscriptionSource {
    const value = this.state.subscriptions.find(item => item.id === id);
    if (!value || ticket !== this.sourceTickets.get(id)) throw new APIError(409, 'Subscription changed during refresh');
    return value;
  }

  private commitRefresh(source: SubscriptionSource, result: Awaited<ReturnType<Fetcher>>): SubscriptionSource {
    const updated = { ...source, lastUpdatedAt: new Date().toISOString(), lastError: null, usage: result.usage,
      name: source.nameWasAutoGenerated && result.name ? result.name : source.name };
    this.update(draft => {
      const previous = draft.nodes.filter(node => node.sourceID === source.id);
      const priorIDs = new Set(previous.map(node => node.id));
      const carried = carriedOverExclusions(previous, draft.excludedNodeIDs, result.nodes);
      draft.nodes = [...draft.nodes.filter(node => !priorIDs.has(node.id)), ...carried.nodes];
      draft.excludedNodeIDs = [...draft.excludedNodeIDs.filter(id => !priorIDs.has(id)), ...carried.excludedNodeIDs];
      draft.subscriptions[draft.subscriptions.findIndex(item => item.id === source.id)] = updated;
    });
    this.scheduleGeo(result.nodes.filter(node => !isSubscriptionMetadata(node)));
    return updated;
  }

  refreshSubscription(id: string): Promise<SubscriptionMutationResult> {
    const existing = this.sourceRefreshes.get(id);
    if (existing) return existing;
    const source = required(this.state.subscriptions.find(item => item.id === id), 'Subscription not found');
    const ticket = Symbol();
    this.sourceTickets.set(id, ticket);
    const task = (async () => {
      let result: Awaited<ReturnType<Fetcher>>;
      try { result = await this.loadSubscription(source); } catch (error) {
        const current = this.currentSource(id, ticket);
        this.update(draft => {
          draft.subscriptions[draft.subscriptions.findIndex(item => item.id === id)] = { ...current, lastError: (error as Error).message };
        });
        throw error;
      }
      const updated = this.commitRefresh(this.currentSource(id, ticket), result);
      return this.response(updated, result);
    })().finally(() => { if (this.sourceRefreshes.get(id) === task) this.sourceRefreshes.delete(id); });
    this.sourceRefreshes.set(id, task);
    return task;
  }

  async refreshAll() {
    const enabled = this.state.subscriptions.filter(source => source.isEnabled);
    const results = await Promise.all(enabled.map(async ({ id, name }) => {
      try {
        const result = await this.refreshSubscription(id);
        return { id, name: result.subscription.name, addedNodeCount: result.addedNodeCount,
          skippedCount: result.skippedCount, skippedReasons: result.skippedReasons };
      } catch (error) { return { id, name, error: (error as Error).message }; }
    }));
    return { results, state: this.stateResponse() };
  }

  importNodes(content: string) {
    const parsed = parseSubscription(content);
    const existing = new Set(this.state.nodes.filter(node => !node.sourceID).map(node => canonicalKey(node)));
    const addedNodes = parsed.nodes.filter(node => !existing.has(canonicalKey(node)));
    if (addedNodes.length) {
      this.update(draft => { draft.nodes.push(...addedNodes); });
      this.scheduleGeo(addedNodes.filter(node => !isSubscriptionMetadata(node)));
    }
    return { addedNodes, skippedCount: parsed.skippedCount, skippedReasons: parsed.skippedReasons, state: this.stateResponse() };
  }

  patchNode(id: string, input: Partial<ProxyNode> & { excluded?: boolean }) {
    const node = required(this.state.nodes.find(item => item.id === id), 'Node not found');
    const { excluded, ...patch } = input;
    const changed = { ...node, ...patch };
    if (changed.countryOverride) {
      const override = changed.countryOverride.trim().toUpperCase();
      changed.countryOverride = override === 'UK' ? 'GB' : override;
      if (!countries.some(item => item.code === changed.countryOverride)) invalid('Unknown country code');
    }
    try { validateNode(changed); } catch (error) { invalid((error as Error).message); }
    this.update(draft => {
      draft.nodes[draft.nodes.findIndex(item => item.id === id)] = changed;
      if (excluded === true) draft.excludedNodeIDs = unique([...draft.excludedNodeIDs, id]);
      if (excluded === false) draft.excludedNodeIDs = draft.excludedNodeIDs.filter(item => item !== id);
    });
    if (changed.server !== node.server || changed.port !== node.port) {
      this.runtime.delete(node.id);
      this.scheduleGeo([changed]);
    }
    return changed;
  }

  removeNode(id: string) {
    const node = required(this.state.nodes.find(item => item.id === id), 'Node not found');
    if (node.sourceID) invalid('Only locally imported nodes can be deleted');
    this.update(draft => {
      draft.nodes = draft.nodes.filter(item => item.id !== id);
      draft.excludedNodeIDs = draft.excludedNodeIDs.filter(item => item !== id);
    });
  }

  getScheme(id: string): RuleScheme { return required(findScheme(this.state, id), 'Rule scheme not found'); }

  runtimeForExport() { return this.runtimeRecord; }
}
