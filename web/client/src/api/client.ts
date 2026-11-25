import type {
  AppSettings,
  AppSnapshot,
  AppStateResponse,
  ClientTarget,
  CountryEntry,
  CustomRuleFlow,
  ExportContentMode,
  GeneratedConfiguration,
  LANSubscriptionFormat,
  LANSharingStatus,
  LocalRuleSet,
  NodeExportNameFilter,
  NodeLatencyMeasurement,
  NodeLatencyTestMode,
  NodeRuntimeInfo,
  ProxyKind,
  ProxyNode,
  ReorderGroupsResponse,
  RuleCatalogEntry,
  RuleScheme,
  RuleSchemeCustomization,
  RuleSchemeGroup,
  RuleSchemeGroupKind,
  RuleSchemeGroupMember,
  RuleSchemesResponseLike,
  SourceDetectionResult,
  SubscriptionMutationResult,
  SubscriptionRequestOptions,
  SubscriptionSource,
  TailnetConnection,
  WorldDotMapData,
} from './apiTypes';

export class ApiError extends Error {
  status: number;
  code?: string;
  details?: unknown;

  constructor(message: string, status: number, code?: string, details?: unknown) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers);
  if (init?.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  const response = await fetch(path, {
    ...init,
    headers,
  });

  if (!response.ok) {
    let errorMessage = `请求失败 (${response.status})`;
    let code: string | undefined;
    let details: unknown;
    try {
      const payload = (await response.json()) as {
        error?: string;
        message?: string;
        code?: string;
        details?: unknown;
      };
      if (payload.error) errorMessage = payload.error;
      else if (payload.message) errorMessage = payload.message;
      code = payload.code;
      details = payload.details;
    } catch {
      const text = await response.text().catch(() => '');
      if (text) errorMessage = text;
    }
    throw new ApiError(errorMessage, response.status, code, details);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export const api = {
  // 4.1 引导、状态与静态资产
  health(): Promise<{ ok: boolean; version: string; resourcesReady: boolean; ipDbVersion?: string }> {
    return request('/api/health');
  },
  getState(): Promise<AppStateResponse> {
    return request('/api/state');
  },
  getWorldMap(): Promise<WorldDotMapData> {
    return request('/api/world-map');
  },
  getCountries(): Promise<CountryEntry[]> {
    return request('/api/countries');
  },

  // 4.2 订阅与节点
  detectSource(input: string): Promise<SourceDetectionResult> {
    return request('/api/sources/detect', {
      method: 'POST',
      body: JSON.stringify({ input }),
    });
  },
  addSubscription(payload: {
    name?: string;
    urlString: string;
    requestOptions?: SubscriptionRequestOptions | null;
  }): Promise<SubscriptionMutationResult> {
    return request('/api/subscriptions', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },
  updateSubscription(
    id: string,
    patch: Partial<{
      name: string;
      urlString: string;
      isEnabled: boolean;
      requestOptions: SubscriptionRequestOptions | null;
    }>
  ): Promise<{ subscription?: SubscriptionSource; state?: AppStateResponse } | AppStateResponse> {
    return request(`/api/subscriptions/${encodeURIComponent(id)}`, {
      method: 'PATCH',
      body: JSON.stringify(patch),
    });
  },
  deleteSubscription(id: string): Promise<{ ok?: boolean; state?: AppStateResponse } | AppStateResponse> {
    return request(`/api/subscriptions/${encodeURIComponent(id)}`, {
      method: 'DELETE',
    });
  },
  refreshSubscription(id: string): Promise<SubscriptionMutationResult> {
    return request(`/api/subscriptions/${encodeURIComponent(id)}/refresh`, {
      method: 'POST',
    });
  },
  refreshAllSubscriptions(): Promise<{
    results?: Array<{ id: string; name?: string; addedNodeCount?: number; error?: string }>;
    state?: AppStateResponse;
  }> {
    return request('/api/subscriptions/refresh-all', {
      method: 'POST',
    });
  },
  importNodes(content: string): Promise<{
    addedNodes: ProxyNode[];
    skippedCount: number;
    skippedReasons: Array<{ reason: string; count: number }>;
    state?: AppStateResponse;
  }> {
    return request('/api/nodes/import', {
      method: 'POST',
      body: JSON.stringify({ content }),
    });
  },
  updateNode(
    id: string,
    patch: Partial<{
      name: string;
      countryOverride: string | null;
      excluded: boolean;
      server: string;
      port: number;
    }>
  ): Promise<{ node?: ProxyNode; state?: AppStateResponse }> {
    return request(`/api/nodes/${encodeURIComponent(id)}`, {
      method: 'PATCH',
      body: JSON.stringify(patch),
    });
  },
  deleteNode(id: string): Promise<{ ok?: boolean; state?: AppStateResponse }> {
    return request(`/api/nodes/${encodeURIComponent(id)}`, {
      method: 'DELETE',
    });
  },
  setNodeExclusions(
    payload: { excludedNodeIDs: string[] } | { nodeIds: string[]; excluded: boolean }
  ): Promise<{ excludedNodeIDs?: string[]; state?: AppStateResponse }> {
    return request('/api/nodes/exclusions', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },
  testNodeLatency(payload: {
    nodeIds?: string[];
    mode?: NodeLatencyTestMode;
  }): Promise<{ results: Record<string, NodeLatencyMeasurement> }> {
    return request('/api/nodes/latency', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },
  resolveNodesGeo(payload?: {
    nodeIds?: string[];
  }): Promise<{ runtime: Record<string, NodeRuntimeInfo> }> {
    return request('/api/nodes/resolve-geo', {
      method: 'POST',
      body: JSON.stringify(payload ?? {}),
    });
  },
  shareNode(id: string): Promise<{ uri: string; qrDataUrl: string }> {
    return request(`/api/nodes/${encodeURIComponent(id)}/share`);
  },
  updateExportNameFilter(filter: NodeExportNameFilter | null): Promise<{
    nodeExportNameFilter?: NodeExportNameFilter | null;
    state?: AppStateResponse;
  }> {
    return request('/api/nodes/export-filter', {
      method: 'PUT',
      body: JSON.stringify(filter ?? { includeKeywords: [], excludeKeywords: [], useRegex: false }),
    });
  },

  // 4.3 规则与策略组
  getRuleSchemes(): Promise<RuleSchemesResponseLike> {
    return request('/api/rules/schemes');
  },
  selectRuleScheme(schemeId: string): Promise<{ state?: AppStateResponse }> {
    return request('/api/rules/selected', {
      method: 'PUT',
      body: JSON.stringify({ schemeId }),
    });
  },
  importRuleScheme(payload: {
    name?: string;
    url?: string;
    content?: string;
  }): Promise<{ scheme?: RuleScheme; state?: AppStateResponse }> {
    return request('/api/rules/schemes/import', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },
  deleteRuleScheme(id: string): Promise<{ ok?: boolean; state?: AppStateResponse }> {
    return request(`/api/rules/schemes/${encodeURIComponent(id)}`, {
      method: 'DELETE',
    });
  },
  refreshRuleScheme(id: string): Promise<{ scheme?: RuleScheme; state?: AppStateResponse }> {
    return request(`/api/rules/schemes/${encodeURIComponent(id)}/refresh`, {
      method: 'POST',
    });
  },
  setEnabledRuleGroups(
    schemeId: string,
    enabledGroupNames: string[] | null
  ): Promise<{ state?: AppStateResponse }> {
    return request(`/api/rules/schemes/${encodeURIComponent(schemeId)}/enabled-groups`, {
      method: 'PUT',
      body: JSON.stringify({ enabledGroupNames }),
    });
  },
  reorderRuleGroups(
    schemeId: string,
    payload: {
      fromIndex: number;
      toIndex: number;
      orderedGroupNames: string[];
    }
  ): Promise<ReorderGroupsResponse> {
    return request(`/api/rules/schemes/${encodeURIComponent(schemeId)}/reorder-groups`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },
  updateSchemeCustomization(
    schemeId: string,
    customization: RuleSchemeCustomization,
    options?: { emojiEnabled?: boolean; emojiAutofill?: boolean }
  ): Promise<{ state?: AppStateResponse }> {
    return request(`/api/rules/schemes/${encodeURIComponent(schemeId)}/customization`, {
      method: 'PUT',
      body: JSON.stringify({
        customization,
        ...options,
      }),
    });
  },
  createRuleGroup(
    schemeId: string,
    group: Partial<RuleSchemeGroup> & { name: string; kind: RuleSchemeGroupKind }
  ): Promise<{ state?: AppStateResponse }> {
    return request(`/api/rules/schemes/${encodeURIComponent(schemeId)}/groups`, {
      method: 'POST',
      body: JSON.stringify(group),
    });
  },
  updateRuleGroup(
    schemeId: string,
    groupName: string,
    patch: {
      newName?: string;
      kind?: RuleSchemeGroupKind;
      members?: RuleSchemeGroupMember[];
      nodePattern?: string;
      supplementRules?: string[];
      isEnabled?: boolean;
    }
  ): Promise<{ state?: AppStateResponse }> {
    return request(
      `/api/rules/schemes/${encodeURIComponent(schemeId)}/groups/${encodeURIComponent(groupName)}`,
      {
        method: 'PATCH',
        body: JSON.stringify(patch),
      }
    );
  },
  deleteRuleGroup(schemeId: string, groupName: string): Promise<{ state?: AppStateResponse }> {
    return request(
      `/api/rules/schemes/${encodeURIComponent(schemeId)}/groups/${encodeURIComponent(groupName)}`,
      {
        method: 'DELETE',
      }
    );
  },
  searchRuleCatalog(params: {
    q?: string;
    source?: 'all' | 'acl4ssr' | 'blackmatrix7';
    limit?: number;
  }): Promise<{ items?: RuleCatalogEntry[]; entries?: RuleCatalogEntry[] } | RuleCatalogEntry[]> {
    const query = new URLSearchParams();
    if (params.q) query.set('q', params.q);
    if (params.source) query.set('source', params.source);
    if (params.limit) query.set('limit', String(params.limit));
    return request(`/api/rules/catalog?${query.toString()}`);
  },
  createRuleFlow(payload: {
    schemeID: string;
    name: string;
    policyName: string;
    rulesText?: string;
    isEnabled?: boolean;
    localRuleSetID?: string | null;
    catalogID?: string | null;
    sourceURLString?: string | null;
    generatedPolicyGroup?: RuleSchemeGroup | null;
    isGroupSupplement?: boolean | null;
  }): Promise<{ flow?: CustomRuleFlow; state?: AppStateResponse }> {
    return request('/api/rules/flows', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },
  updateRuleFlow(
    id: string,
    patch: Partial<CustomRuleFlow>
  ): Promise<{ flow?: CustomRuleFlow; state?: AppStateResponse }> {
    return request(`/api/rules/flows/${encodeURIComponent(id)}`, {
      method: 'PATCH',
      body: JSON.stringify(patch),
    });
  },
  deleteRuleFlow(id: string): Promise<{ ok?: boolean; state?: AppStateResponse }> {
    return request(`/api/rules/flows/${encodeURIComponent(id)}`, {
      method: 'DELETE',
    });
  },
  getLocalRuleSets(): Promise<LocalRuleSet[] | { localRuleSets: LocalRuleSet[] }> {
    return request('/api/rules/local-sets');
  },
  createLocalRuleSet(payload: {
    name: string;
    rulesText: string;
    sourceURLString?: string | null;
  }): Promise<{ localRuleSet?: LocalRuleSet; state?: AppStateResponse }> {
    return request('/api/rules/local-sets', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },
  updateLocalRuleSet(
    id: string,
    patch: Partial<LocalRuleSet>
  ): Promise<{ localRuleSet?: LocalRuleSet; state?: AppStateResponse }> {
    return request(`/api/rules/local-sets/${encodeURIComponent(id)}`, {
      method: 'PATCH',
      body: JSON.stringify(patch),
    });
  },
  deleteLocalRuleSet(id: string): Promise<{ ok?: boolean; state?: AppStateResponse }> {
    return request(`/api/rules/local-sets/${encodeURIComponent(id)}`, {
      method: 'DELETE',
    });
  },

  // 4.4 配置导出与订阅分发
  generateConfig(payload: {
    target: ClientTarget;
    mode: ExportContentMode;
    maskSecrets?: boolean;
  }): Promise<GeneratedConfiguration> {
    return request('/api/export/generate', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },
  downloadUrl(target: ClientTarget, mode: ExportContentMode): string {
    const query = new URLSearchParams({ target, mode });
    return `/api/export/download?${query.toString()}`;
  },
  updateExportPreferences(patch: {
    selectedTarget?: ClientTarget;
    visibleClientTargets?: ClientTarget[];
    clientOrder?: ClientTarget[];
    lanSharingOrderIndex?: number;
    isLANSharingVisible?: boolean;
    exportContentModes?: Record<string, ExportContentMode>;
    excludedKinds?: Record<string, ProxyKind[]>;
  }): Promise<{ state?: AppStateResponse }> {
    return request('/api/export/preferences', {
      method: 'PUT',
      body: JSON.stringify(patch),
    });
  },
  getLanStatus(): Promise<LANSharingStatus> {
    return request('/api/lan/status');
  },
  toggleLan(enabled: boolean): Promise<LANSharingStatus> {
    return request('/api/lan/toggle', {
      method: 'POST',
      body: JSON.stringify({ enabled }),
    });
  },
  rotateLanToken(): Promise<LANSharingStatus> {
    return request('/api/lan/rotate-token', {
      method: 'POST',
    });
  },
  getLanQr(params: {
    format: LANSubscriptionFormat;
    content: ExportContentMode;
    baseUrl?: string;
  }): Promise<{ url: string; qrDataUrl: string }> {
    const query = new URLSearchParams({
      format: params.format,
      content: params.content,
    });
    if (params.baseUrl) query.set('baseUrl', params.baseUrl);
    return request(`/api/lan/qr?${query.toString()}`);
  },

  // 4.5 设置、Tailscale 与快照管理
  updateSettings(patch: Partial<AppSettings>): Promise<{ settings?: AppSettings; state?: AppStateResponse }> {
    return request('/api/settings', {
      method: 'PATCH',
      body: JSON.stringify(patch),
    });
  },
  getTailnets(): Promise<TailnetConnection[] | { tailnets: TailnetConnection[] }> {
    return request('/api/tailnets');
  },
  createTailnet(
    payload: Omit<TailnetConnection, 'id' | 'hasAuthKey' | 'stableSlug'> & { authKey?: string | null }
  ): Promise<{ tailnet?: TailnetConnection; state?: AppStateResponse }> {
    return request('/api/tailnets', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },
  updateTailnet(
    id: string,
    patch: Partial<Omit<TailnetConnection, 'id' | 'hasAuthKey' | 'stableSlug'>> & {
      authKey?: string | null;
    }
  ): Promise<{ tailnet?: TailnetConnection; state?: AppStateResponse }> {
    return request(`/api/tailnets/${encodeURIComponent(id)}`, {
      method: 'PATCH',
      body: JSON.stringify(patch),
    });
  },
  deleteTailnet(id: string): Promise<{ ok?: boolean; state?: AppStateResponse }> {
    return request(`/api/tailnets/${encodeURIComponent(id)}`, {
      method: 'DELETE',
    });
  },
  exportSnapshot(): Promise<AppSnapshot> {
    return request('/api/snapshot/export');
  },
  importSnapshot(snapshot: AppSnapshot): Promise<{ ok?: boolean; state?: AppStateResponse }> {
    return request('/api/snapshot/import', {
      method: 'POST',
      body: JSON.stringify(snapshot),
    });
  },
  resetSnapshot(): Promise<{ ok?: boolean; state?: AppStateResponse }> {
    return request('/api/snapshot/reset', {
      method: 'POST',
    });
  },
};
