import { useMemo, useState } from 'react';
import {
  Activity,
  Check,
  Copy,
  Filter,
  Globe,
  Loader2,
  QrCode,
  Search,
  Trash2,
  X,
} from 'lucide-react';
import { api } from '@/api/client';
import { Modal } from '@/components/Modal';
import { SelectionIndicator } from '@/components/SelectionIndicator';
import { useTower } from '@/state/TowerContext';
import { PROXY_KIND_LABELS, type ProxyKind, type ProxyNode } from '@shared/index';

export function NodeSection() {
  const {
    state,
    countries,
    selectedCountryCode,
    setSelectedCountryCode,
    testingNodeIds,
    testLatency,
    applyStateFromMutation,
    showToast,
  } = useTower();

  const [search, setSearch] = useState('');
  const [selectedKind, setSelectedKind] = useState<ProxyKind | 'all'>('all');

  // Export name filter modal
  const [filterModalOpen, setFilterModalOpen] = useState(false);
  const [includeText, setIncludeText] = useState('');
  const [excludeText, setExcludeText] = useState('');
  const [useRegex, setUseRegex] = useState(false);

  // Manual country picker modal
  const [countryPickerNode, setCountryPickerNode] = useState<ProxyNode | null>(null);
  const [countrySearch, setCountrySearch] = useState('');

  // Share QR modal
  const [shareModal, setShareModal] = useState<{
    node: ProxyNode;
    uri: string;
    qrDataUrl: string;
  } | null>(null);
  const [shareLoadingId, setShareLoadingId] = useState<string | null>(null);

  const allNodes = useMemo(
    () => (state?.snapshot.nodes ?? []).filter((n) => !n.isSubscriptionMetadata),
    [state?.snapshot.nodes]
  );

  const enabledSubs = useMemo(
    () =>
      new Set(
        (state?.snapshot.subscriptions ?? []).filter((s) => s.isEnabled).map((s) => s.id)
      ),
    [state?.snapshot.subscriptions]
  );

  const excludedSet = useMemo(
    () => new Set(state?.snapshot.excludedNodeIDs ?? []),
    [state?.snapshot.excludedNodeIDs]
  );

  const availableKinds = useMemo(() => {
    const set = new Set<ProxyKind>();
    for (const n of allNodes) set.add(n.kind);
    return Array.from(set);
  }, [allNodes]);

  const filteredNodes = useMemo(() => {
    const q = search.trim().toLowerCase();
    return allNodes.filter((node) => {
      if (node.sourceID && !enabledSubs.has(node.sourceID)) return false;
      if (selectedKind !== 'all' && node.kind !== selectedKind) return false;
      const rt = state?.runtime[node.id];
      const code = (node.countryOverride || rt?.countryCode || '').toUpperCase();
      if (selectedCountryCode && code !== selectedCountryCode.toUpperCase()) return false;
      if (q) {
        const hay = `${node.name} ${node.server} ${rt?.countryName ?? ''} ${rt?.asnOrg ?? ''} ${node.kind}`.toLowerCase();
        if (!hay.includes(q)) return false;
      }
      return true;
    });
  }, [allNodes, enabledSubs, search, selectedCountryCode, selectedKind, state?.runtime]);

  const toggleNodeExcluded = async (node: ProxyNode) => {
    const currentlyExcluded = excludedSet.has(node.id);
    try {
      const res = await api.setNodeExclusions({
        nodeIds: [node.id],
        excluded: !currentlyExcluded,
      });
      await applyStateFromMutation(res.state);
    } catch (err) {
      showToast(err instanceof Error ? err.message : '更新节点状态失败', 'danger');
    }
  };

  const batchSetExcluded = async (excluded: boolean) => {
    const ids = filteredNodes.map((n) => n.id);
    if (ids.length === 0) return;
    try {
      const res = await api.setNodeExclusions({ nodeIds: ids, excluded });
      await applyStateFromMutation(res.state);
      showToast(excluded ? `已排除 ${ids.length} 个节点` : `已启用 ${ids.length} 个节点`, 'neutral');
    } catch (err) {
      showToast(err instanceof Error ? err.message : '批量更新失败', 'danger');
    }
  };

  const handleDeleteNode = async (node: ProxyNode) => {
    try {
      const res = await api.deleteNode(node.id);
      await applyStateFromMutation(res.state);
      showToast(`已删除自有节点「${node.name}」`, 'neutral');
    } catch (err) {
      showToast(err instanceof Error ? err.message : '删除节点失败', 'danger');
    }
  };

  const handleOpenShare = async (node: ProxyNode) => {
    setShareLoadingId(node.id);
    try {
      const res = await api.shareNode(node.id);
      setShareModal({ node, uri: res.uri, qrDataUrl: res.qrDataUrl });
    } catch (err) {
      showToast(err instanceof Error ? err.message : '生成分享二维码失败', 'danger');
    } finally {
      setShareLoadingId(null);
    }
  };

  const handleSelectCountry = async (countryCode: string | null) => {
    if (!countryPickerNode) return;
    try {
      const res = await api.updateNode(countryPickerNode.id, {
        countryOverride: countryCode,
      });
      await applyStateFromMutation(res.state);
      setCountryPickerNode(null);
      showToast(
        countryCode ? `已将「${countryPickerNode.name}」地区设为 ${countryCode}` : '已恢复自动地区识别',
        'success'
      );
    } catch (err) {
      showToast(err instanceof Error ? err.message : '修改地区失败', 'danger');
    }
  };

  const openExportFilterModal = () => {
    const current = state?.snapshot.nodeExportNameFilter;
    setIncludeText((current?.includeKeywords ?? []).join(', '));
    setExcludeText((current?.excludeKeywords ?? []).join(', '));
    setUseRegex(Boolean(current?.useRegex));
    setFilterModalOpen(true);
  };

  const saveExportFilter = async () => {
    const parseList = (raw: string) =>
      raw
        .split(/[,，\n]/)
        .map((s) => s.trim())
        .filter(Boolean);
    try {
      const res = await api.updateExportNameFilter({
        includeKeywords: parseList(includeText),
        excludeKeywords: parseList(excludeText),
        useRegex,
      });
      await applyStateFromMutation(res.state);
      setFilterModalOpen(false);
      showToast('已更新全局导出名称筛选', 'success');
    } catch (err) {
      showToast(err instanceof Error ? err.message : '更新筛选失败', 'danger');
    }
  };

  const filteredCountries = useMemo(() => {
    const q = countrySearch.trim().toLowerCase();
    if (!q) return countries;
    return countries.filter(
      (c) =>
        c.chineseName.toLowerCase().includes(q) ||
        c.englishName.toLowerCase().includes(q) ||
        c.code.toLowerCase().includes(q)
    );
  }, [countries, countrySearch]);

  const activeExportFilterCount =
    (state?.snapshot.nodeExportNameFilter?.includeKeywords?.length ?? 0) +
    (state?.snapshot.nodeExportNameFilter?.excludeKeywords?.length ?? 0);

  return (
    <section className="space-y-3">
      {/* Header & Controls */}
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <h2 className="text-lg font-semibold tracking-tight text-[var(--tower-text)]">
            节点列表
          </h2>
          <span className="text-xs font-medium text-[var(--tower-text-secondary)] tabular-nums">
            {filteredNodes.length} 个
          </span>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={openExportFilterModal}
            className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium border transition-colors cursor-pointer ${
              activeExportFilterCount > 0
                ? 'bg-[var(--tower-accent-soft)] border-[var(--tower-accent)]/30 text-[var(--tower-accent)]'
                : 'bg-[var(--tower-surface)] border-[var(--tower-border)] text-[var(--tower-text-secondary)] hover:text-[var(--tower-text)]'
            }`}
          >
            <Filter className="w-3.5 h-3.5" />
            <span>导出名称筛选{activeExportFilterCount > 0 ? ` (${activeExportFilterCount})` : ''}</span>
          </button>

          {filteredNodes.length > 0 && (
            <>
              <button
                type="button"
                onClick={() => void batchSetExcluded(false)}
                className="px-2.5 py-1.5 rounded-full text-xs font-medium bg-[var(--tower-surface)] border border-[var(--tower-border)] text-[var(--tower-text-secondary)] hover:text-[var(--tower-text)] cursor-pointer"
              >
                全选当前
              </button>
              <button
                type="button"
                onClick={() => void batchSetExcluded(true)}
                className="px-2.5 py-1.5 rounded-full text-xs font-medium bg-[var(--tower-surface)] border border-[var(--tower-border)] text-[var(--tower-text-secondary)] hover:text-[var(--tower-text)] cursor-pointer"
              >
                排除当前
              </button>
            </>
          )}
        </div>
      </div>

      {/* Search + Protocol Filter Bar */}
      <div className="tower-card p-3.5 space-y-3">
        <div className="flex flex-wrap items-center gap-2.5">
          <div className="relative flex-1 min-w-[200px]">
            <Search className="w-4 h-4 text-[var(--tower-text-tertiary)] absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="搜索节点名称、地址、国家或 ASN…"
              className="w-full pl-9 pr-8 py-2 text-sm tower-input"
            />
            {search && (
              <button
                type="button"
                onClick={() => setSearch('')}
                aria-label="清空搜索"
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-[var(--tower-text-tertiary)] hover:text-[var(--tower-text)] cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            )}
          </div>

          {selectedCountryCode && (
            <button
              type="button"
              onClick={() => setSelectedCountryCode(null)}
              className="inline-flex items-center gap-1.5 px-3 py-2 rounded-[12px] bg-[var(--tower-accent-soft)] text-[var(--tower-accent)] text-xs font-semibold cursor-pointer"
            >
              <Globe className="w-3.5 h-3.5" />
              <span>仅看地区: {selectedCountryCode}</span>
              <X className="w-3.5 h-3.5" />
            </button>
          )}
        </div>

        {/* Protocol Pills */}
        {availableKinds.length > 1 && (
          <div className="flex items-center gap-1.5 overflow-x-auto pb-0.5">
            <button
              type="button"
              onClick={() => setSelectedKind('all')}
              className={`shrink-0 px-2.5 py-1 rounded-full text-xs font-medium transition-colors cursor-pointer ${
                selectedKind === 'all'
                  ? 'bg-[var(--tower-accent)] text-white'
                  : 'bg-[var(--tower-surface-subtle)] text-[var(--tower-text-secondary)] hover:text-[var(--tower-text)]'
              }`}
            >
              全部协议
            </button>
            {availableKinds.map((kind) => (
              <button
                key={kind}
                type="button"
                onClick={() => setSelectedKind(kind === selectedKind ? 'all' : kind)}
                className={`shrink-0 px-2.5 py-1 rounded-full text-xs font-medium transition-colors cursor-pointer ${
                  selectedKind === kind
                    ? 'bg-[var(--tower-accent)] text-white'
                    : 'bg-[var(--tower-surface-subtle)] text-[var(--tower-text-secondary)] hover:text-[var(--tower-text)]'
                }`}
              >
                {PROXY_KIND_LABELS[kind] ?? kind}
              </button>
            ))}
          </div>
        )}

        {/* Nodes List */}
        {filteredNodes.length === 0 ? (
          <div className="py-10 text-center text-sm text-[var(--tower-text-secondary)]">
            没有符合当前筛选条件的节点
          </div>
        ) : (
          <div className="divide-y divide-[var(--tower-border)]">
            {filteredNodes.map((node) => {
              const rt = state?.runtime[node.id];
              const isExcluded = excludedSet.has(node.id);
              const isTesting = testingNodeIds.has(node.id);
              const ms = rt?.latency?.milliseconds;
              const method = rt?.latency?.method;
              const flag = rt?.flagEmoji ?? '';
              const countryCode = (node.countryOverride || rt?.countryCode || '').toUpperCase();
              const isManualCountry = Boolean(node.countryOverride);
              const isLocalNode = !node.sourceID;

              return (
                <div
                  key={node.id}
                  className={`py-2.5 flex items-center gap-3 transition-opacity ${
                    isExcluded ? 'opacity-50' : ''
                  }`}
                >
                  <SelectionIndicator
                    selected={!isExcluded}
                    onToggle={() => void toggleNodeExcluded(node)}
                    label={isExcluded ? `启用 ${node.name}` : `排除 ${node.name}`}
                    size="sm"
                  />

                  {/* Plain flag emoji — NO circle background per CLAUDE.md #20 */}
                  <button
                    type="button"
                    onClick={() => {
                      setCountrySearch('');
                      setCountryPickerNode(node);
                    }}
                    title={
                      isManualCountry
                        ? `手动指定地区 (${countryCode})，点击修改`
                        : '点击手动指定地区'
                    }
                    className="w-7 text-center text-lg leading-none shrink-0 hover:scale-110 transition-transform cursor-pointer"
                  >
                    {flag || '🌐'}
                  </button>

                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span
                        className={`text-sm font-medium text-[var(--tower-text)] truncate max-w-full ${
                          isExcluded ? 'line-through' : ''
                        }`}
                      >
                        {node.name}
                      </span>
                      <span className="px-1.5 py-0.5 rounded-[6px] bg-[var(--tower-surface-subtle)] text-[10px] font-semibold text-[var(--tower-text-secondary)]">
                        {PROXY_KIND_LABELS[node.kind] ?? node.kind}
                      </span>
                      {isManualCountry && (
                        <span className="px-1.5 py-0.5 rounded-[6px] bg-[var(--tower-accent-soft)] text-[10px] font-medium text-[var(--tower-accent)]">
                          手动地区
                        </span>
                      )}
                      {isLocalNode && (
                        <span className="px-1.5 py-0.5 rounded-[6px] bg-purple-500/10 text-[10px] font-medium text-purple-600 dark:text-purple-400">
                          自有
                        </span>
                      )}
                    </div>
                    <div className="mt-0.5 flex items-center gap-2.5 text-[11px] text-[var(--tower-text-secondary)] truncate">
                      <span className="font-mono truncate">
                        {node.server}:{node.port}
                      </span>
                      {rt?.asnOrg && <span className="truncate">· {rt.asnOrg}</span>}
                    </div>
                  </div>

                  {/* Latency Pill */}
                  <button
                    type="button"
                    onClick={() => void testLatency([node.id])}
                    disabled={isTesting}
                    title="点击单独测速"
                    className={`shrink-0 inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold tabular-nums transition-colors cursor-pointer ${
                      isTesting
                        ? 'bg-[var(--tower-surface-subtle)] text-[var(--tower-text-secondary)]'
                        : ms != null
                          ? ms < 80
                            ? 'bg-emerald-500/12 text-emerald-600 dark:text-emerald-400'
                            : ms < 180
                              ? 'bg-amber-500/12 text-amber-600 dark:text-amber-400'
                              : 'bg-red-500/12 text-red-600 dark:text-red-400'
                          : rt?.latency
                            ? 'bg-red-500/10 text-red-500'
                            : 'bg-[var(--tower-surface-subtle)] text-[var(--tower-text-secondary)] hover:text-[var(--tower-text)]'
                    }`}
                  >
                    {isTesting ? (
                      <Loader2 className="w-3 h-3 animate-spin" />
                    ) : ms != null ? (
                      <>
                        <span>{ms} ms</span>
                        {method && (
                          <span className="text-[10px] opacity-75 font-normal">{method}</span>
                        )}
                      </>
                    ) : rt?.latency ? (
                      <span>超时</span>
                    ) : (
                      <>
                        <Activity className="w-3 h-3" />
                        <span>测速</span>
                      </>
                    )}
                  </button>

                  {/* Share Button */}
                  <button
                    type="button"
                    onClick={() => void handleOpenShare(node)}
                    disabled={shareLoadingId === node.id}
                    aria-label="分享节点二维码"
                    title="分享节点"
                    className="w-8 h-8 rounded-full flex items-center justify-center text-[var(--tower-text-secondary)] hover:text-[var(--tower-accent)] hover:bg-[var(--tower-accent-soft)] transition-colors cursor-pointer shrink-0"
                  >
                    {shareLoadingId === node.id ? (
                      <Loader2 className="w-4 h-4 animate-spin" />
                    ) : (
                      <QrCode className="w-4 h-4" />
                    )}
                  </button>

                  {isLocalNode && (
                    <button
                      type="button"
                      onClick={() => void handleDeleteNode(node)}
                      aria-label="删除自有节点"
                      title="删除自有节点"
                      className="w-8 h-8 rounded-full flex items-center justify-center text-[var(--tower-text-secondary)] hover:text-red-500 hover:bg-red-500/10 transition-colors cursor-pointer shrink-0"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Global Export Name Filter Modal */}
      <Modal
        open={filterModalOpen}
        onClose={() => setFilterModalOpen(false)}
        title="全局导出名称筛选"
        subtitle="筛选导出配置与局域网订阅时保留或排除的节点名称关键字"
        footer={
          <>
            <button
              type="button"
              onClick={() => setFilterModalOpen(false)}
              className="px-4 py-2 rounded-[12px] text-sm font-medium text-[var(--tower-text-secondary)] hover:text-[var(--tower-text)] cursor-pointer"
            >
              取消
            </button>
            <button
              type="button"
              onClick={() => void saveExportFilter()}
              className="px-4 py-2 rounded-[12px] bg-[var(--tower-accent)] text-white text-sm font-semibold hover:opacity-92 cursor-pointer"
            >
              保存筛选
            </button>
          </>
        }
      >
        <div className="space-y-4">
          <div>
            <label className="block text-xs font-medium text-[var(--tower-text-secondary)] mb-1.5">
              仅保留包含以下关键字的节点（逗号或换行分隔，留空不限制）
            </label>
            <textarea
              rows={2}
              value={includeText}
              onChange={(e) => setIncludeText(e.target.value)}
              placeholder="例如：IPLC, IEPL, 专线"
              className="w-full p-3 text-sm tower-input"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-[var(--tower-text-secondary)] mb-1.5">
              排除包含以下关键字的节点（逗号或换行分隔）
            </label>
            <textarea
              rows={2}
              value={excludeText}
              onChange={(e) => setExcludeText(e.target.value)}
              placeholder="例如：倍率, 实验性, 过期"
              className="w-full p-3 text-sm tower-input"
            />
          </div>
          <label className="flex items-center gap-2.5 text-sm text-[var(--tower-text)] cursor-pointer select-none">
            <input
              type="checkbox"
              checked={useRegex}
              onChange={(e) => setUseRegex(e.target.checked)}
              className="rounded accent-[var(--tower-accent)] w-4 h-4"
            />
            <span>将上述关键字作为正则表达式匹配</span>
          </label>
        </div>
      </Modal>

      {/* Manual Country Picker Modal */}
      <Modal
        open={countryPickerNode !== null}
        onClose={() => setCountryPickerNode(null)}
        title="手动指定节点地区"
        subtitle={countryPickerNode?.name}
      >
        <div className="space-y-3">
          <div className="relative">
            <Search className="w-4 h-4 text-[var(--tower-text-tertiary)] absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={countrySearch}
              onChange={(e) => setCountrySearch(e.target.value)}
              placeholder="搜索国家/地区名称或两位代码 (如 JP, US, HK)…"
              className="w-full pl-9 pr-3 py-2 text-sm tower-input"
            />
          </div>

          <button
            type="button"
            onClick={() => void handleSelectCountry(null)}
            className="w-full flex items-center justify-between px-3.5 py-2.5 rounded-[12px] bg-[var(--tower-surface-subtle)] hover:bg-[var(--tower-border)] text-sm font-medium text-[var(--tower-text)] cursor-pointer"
          >
            <span>恢复自动识别（节点名 → 离线 IP 库）</span>
            {!countryPickerNode?.countryOverride && (
              <Check className="w-4 h-4 text-[var(--tower-accent)]" />
            )}
          </button>

          <div className="max-h-72 overflow-y-auto divide-y divide-[var(--tower-border)] rounded-[12px] border border-[var(--tower-border)]">
            {filteredCountries.map((c) => {
              const isCurrent = countryPickerNode?.countryOverride?.toUpperCase() === c.code.toUpperCase();
              return (
                <button
                  key={c.code}
                  type="button"
                  onClick={() => void handleSelectCountry(c.code)}
                  className="w-full flex items-center justify-between px-3.5 py-2.5 text-sm hover:bg-[var(--tower-surface-subtle)] transition-colors cursor-pointer text-left"
                >
                  <div className="flex items-center gap-2.5">
                    <span className="text-base leading-none">{c.flag}</span>
                    <span className="font-medium text-[var(--tower-text)]">{c.chineseName}</span>
                    <span className="text-xs text-[var(--tower-text-secondary)]">
                      {c.englishName}
                    </span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-mono text-[var(--tower-text-tertiary)]">
                      {c.code}
                    </span>
                    {isCurrent && <Check className="w-4 h-4 text-[var(--tower-accent)]" />}
                  </div>
                </button>
              );
            })}
          </div>
        </div>
      </Modal>

      {/* Node Share QR Modal */}
      <Modal
        open={shareModal !== null}
        onClose={() => setShareModal(null)}
        title="分享节点"
        subtitle={shareModal?.node.name}
        maxWidthClass="max-w-sm"
      >
        {shareModal && (
          <div className="space-y-4 text-center">
            <div className="inline-block p-3 rounded-[18px] bg-white border border-[var(--tower-border)] shadow-sm">
              <img
                src={shareModal.qrDataUrl}
                alt={shareModal.node.name}
                className="w-52 h-52 object-contain"
              />
            </div>

            <div className="p-3 rounded-[12px] bg-[var(--tower-surface-subtle)] border border-[var(--tower-border)] text-left font-mono text-[11px] text-[var(--tower-text-secondary)] break-all max-h-24 overflow-y-auto">
              {shareModal.uri}
            </div>

            <button
              type="button"
              onClick={() => {
                void navigator.clipboard.writeText(shareModal.uri);
                showToast('已复制节点分享链接', 'success');
              }}
              className="w-full py-2.5 rounded-[12px] bg-[var(--tower-accent)] text-white text-sm font-semibold inline-flex items-center justify-center gap-1.5 hover:opacity-92 cursor-pointer"
            >
              <Copy className="w-4 h-4" />
              <span>复制节点链接</span>
            </button>
          </div>
        )}
      </Modal>
    </section>
  );
}
