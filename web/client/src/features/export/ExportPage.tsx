import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle,
  CheckCircle2,
  Copy,
  Download,
  ExternalLink,
  Filter,
  Info,
  Loader2,
  SlidersHorizontal,
  Wifi,
} from 'lucide-react';
import { api } from '@/api/client';
import { ClientIcon } from '@/components/ClientIcon';
import { Modal } from '@/components/Modal';
import { SelectionIndicator } from '@/components/SelectionIndicator';
import { ClientPickerFilterModal } from '@/features/export/ClientPickerFilterModal';
import { ConfigurationViewer } from '@/features/export/ConfigurationViewer';
import { LanSharingPanel } from '@/features/export/LanSharingPanel';
import { useTower } from '@/state/TowerContext';
import {
  ALL_PROXY_KINDS,
  CLIENT_TARGETS,
  DEFAULT_CLIENT_ORDER,
  PROXY_KIND_LABELS,
  type ClientTarget,
  type ExportContentMode,
  type GeneratedConfiguration,
  type ProxyKind,
} from '@shared/index';

export function ExportPage() {
  const { state, applyStateFromMutation, showToast } = useTower();

  const [selectedCard, setSelectedCard] = useState<'lan' | ClientTarget>('shadowrocket');
  const [filterModalOpen, setFilterModalOpen] = useState(false);
  const [skippedModalOpen, setSkippedModalOpen] = useState(false);
  const [protocolModalOpen, setProtocolModalOpen] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [generated, setGenerated] = useState<GeneratedConfiguration | null>(null);
  const [genError, setGenError] = useState<string | null>(null);

  // Sync initial selected target from snapshot
  useEffect(() => {
    if (state?.snapshot.selectedTarget && selectedCard !== 'lan') {
      setSelectedCard(state.snapshot.selectedTarget);
    }
  }, [state?.snapshot.selectedTarget]);

  const orderedCards = useMemo(() => {
    if (!state) return [];
    const order =
      state.snapshot.clientOrder.length > 0 ? state.snapshot.clientOrder : DEFAULT_CLIENT_ORDER;
    const visibleSet = new Set(
      state.snapshot.visibleClientTargets.length > 0
        ? state.snapshot.visibleClientTargets
        : DEFAULT_CLIENT_ORDER
    );
    const visibleClients = order.filter((t) => visibleSet.has(t));
    const list: Array<'lan' | ClientTarget> = [...visibleClients];
    if (state.snapshot.isLANSharingVisible !== false) {
      const idx = Math.min(Math.max(0, state.snapshot.lanSharingOrderIndex ?? 0), list.length);
      list.splice(idx, 0, 'lan');
    }
    return list;
  }, [state]);

  const activeTarget: ClientTarget | null = selectedCard === 'lan' ? null : selectedCard;
  const activeMeta = activeTarget ? CLIENT_TARGETS[activeTarget] : null;

  const contentMode: ExportContentMode = useMemo(() => {
    if (!activeTarget || !activeMeta) return 'fullConfiguration';
    if (!activeMeta.supportsFullConfiguration) return 'nodesOnly';
    if (!activeMeta.supportsNodesOnly) return 'fullConfiguration';
    return state?.snapshot.exportContentModes[activeTarget] ?? 'fullConfiguration';
  }, [activeMeta, activeTarget, state?.snapshot.exportContentModes]);

  const excludedKindsForTarget: ProxyKind[] = useMemo(() => {
    if (!activeTarget) return [];
    return state?.snapshot.excludedKinds[activeTarget] ?? [];
  }, [activeTarget, state?.snapshot.excludedKinds]);

  const fetchGenerated = useCallback(
    async (target: ClientTarget, mode: ExportContentMode) => {
      setGenerating(true);
      setGenError(null);
      try {
        const res = await api.generateConfig({ target, mode, maskSecrets: true });
        setGenerated(res);
      } catch (err) {
        setGenerated(null);
        setGenError(err instanceof Error ? err.message : '生成配置失败');
      } finally {
        setGenerating(false);
      }
    },
    []
  );

  useEffect(() => {
    if (activeTarget) {
      void fetchGenerated(activeTarget, contentMode);
    }
  }, [
    activeTarget,
    contentMode,
    fetchGenerated,
    state?.snapshot.selectedPresetID,
    state?.snapshot.nodes,
    state?.snapshot.excludedNodeIDs,
    state?.snapshot.excludedKinds,
    state?.snapshot.ruleSchemeCustomizations,
    state?.snapshot.selectedRuleGroups,
    state?.snapshot.tailnets,
    state?.snapshot.settings,
  ]);

  const handleSelectTarget = async (card: 'lan' | ClientTarget) => {
    setSelectedCard(card);
    if (card !== 'lan') {
      try {
        const res = await api.updateExportPreferences({ selectedTarget: card });
        if (res.state) await applyStateFromMutation(res.state);
      } catch {
        // non-blocking
      }
    }
  };

  const handleChangeMode = async (mode: ExportContentMode) => {
    if (!activeTarget || !state) return;
    try {
      const res = await api.updateExportPreferences({
        exportContentModes: {
          ...state.snapshot.exportContentModes,
          [activeTarget]: mode,
        },
      });
      await applyStateFromMutation(res.state);
    } catch (err) {
      showToast(err instanceof Error ? err.message : '切换模式失败', 'danger');
    }
  };

  const handleToggleExcludedKind = async (kind: ProxyKind) => {
    if (!activeTarget || !state) return;
    const current = state.snapshot.excludedKinds[activeTarget] ?? [];
    const next = current.includes(kind)
      ? current.filter((k) => k !== kind)
      : [...current, kind];

    try {
      const res = await api.updateExportPreferences({
        excludedKinds: {
          ...state.snapshot.excludedKinds,
          [activeTarget]: next,
        },
      });
      await applyStateFromMutation(res.state);
    } catch (err) {
      showToast(err instanceof Error ? err.message : '更新协议筛选失败', 'danger');
    }
  };

  const handleCopyConfig = async () => {
    if (!generated?.content) return;
    try {
      await navigator.clipboard.writeText(generated.content);
      showToast(`已复制 ${activeMeta?.name ?? ''} 配置到剪贴板`, 'success');
    } catch {
      showToast('复制失败，请手动在预览区全选复制', 'warning');
    }
  };

  const isQuanxFull = activeTarget === 'quanx' && contentMode === 'fullConfiguration';
  const canShowDirectImport = Boolean(generated?.directImportURL && !isQuanxFull);

  return (
    <div className="space-y-5">
      {/* Client Picker Header */}
      <section className="space-y-2.5">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-lg font-semibold tracking-tight text-[var(--tower-text)]">
              导出配置
            </h1>
            <p className="text-xs text-[var(--tower-text-secondary)]">
              按目标客户端协议能力保真转换与导出
            </p>
          </div>
          <button
            type="button"
            onClick={() => setFilterModalOpen(true)}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-[var(--tower-surface)] border border-[var(--tower-border)] text-xs font-medium text-[var(--tower-text)] hover:bg-[var(--tower-surface-subtle)] cursor-pointer"
          >
            <SlidersHorizontal className="w-3.5 h-3.5 text-[var(--tower-accent)]" />
            <span>筛选与排序客户端</span>
          </button>
        </div>

        {/* Horizontal Scrollable Client Cards */}
        <div className="flex items-stretch gap-2.5 overflow-x-auto pb-2">
          {orderedCards.map((card) => {
            if (card === 'lan') {
              const selected = selectedCard === 'lan';
              return (
                <button
                  key="lan"
                  type="button"
                  onClick={() => void handleSelectTarget('lan')}
                  className={`shrink-0 w-[132px] p-3 rounded-[18px] border text-left transition-all cursor-pointer flex flex-col justify-between gap-2.5 ${
                    selected
                      ? 'bg-[var(--tower-surface)] border-[var(--tower-accent)] ring-2 ring-[var(--tower-accent-soft)] shadow-sm'
                      : 'bg-[var(--tower-surface)] border-[var(--tower-border)] hover:border-[var(--tower-border-strong)]'
                  }`}
                >
                  <div className="w-9 h-9 rounded-[10px] bg-[var(--tower-accent-soft)] text-[var(--tower-accent)] flex items-center justify-center">
                    <Wifi className="w-5 h-5" />
                  </div>
                  <div>
                    <div className="text-xs font-semibold text-[var(--tower-text)] truncate">
                      局域网共享
                    </div>
                    <div className="text-[11px] text-[var(--tower-text-secondary)] truncate">
                      扫码与订阅链接
                    </div>
                  </div>
                </button>
              );
            }

            const meta = CLIENT_TARGETS[card];
            if (!meta) return null;
            const selected = selectedCard === card;
            return (
              <button
                key={card}
                type="button"
                onClick={() => void handleSelectTarget(card)}
                className={`shrink-0 w-[132px] p-3 rounded-[18px] border text-left transition-all cursor-pointer flex flex-col justify-between gap-2.5 ${
                  selected
                    ? 'bg-[var(--tower-surface)] border-[var(--tower-accent)] ring-2 ring-[var(--tower-accent-soft)] shadow-sm'
                    : 'bg-[var(--tower-surface)] border-[var(--tower-border)] hover:border-[var(--tower-border-strong)]'
                }`}
              >
                <ClientIcon
                  iconFile={meta.iconFile}
                  name={meta.name}
                  brandColorHex={meta.brandColorHex}
                  className="w-9 h-9"
                />
                <div>
                  <div className="text-xs font-semibold text-[var(--tower-text)] truncate">
                    {meta.name}
                  </div>
                  <div className="text-[11px] text-[var(--tower-text-secondary)] truncate">
                    {meta.subtitle}
                  </div>
                </div>
              </button>
            );
          })}
        </div>
      </section>

      {selectedCard === 'lan' ? (
        <LanSharingPanel />
      ) : (
        activeMeta && (
          <div className="space-y-4">
            {/* Client Action & Mode Bar */}
            <div className="tower-card p-4 sm:p-5 space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  <ClientIcon
                    iconFile={activeMeta.iconFile}
                    name={activeMeta.name}
                    brandColorHex={activeMeta.brandColorHex}
                    className="w-11 h-11"
                  />
                  <div>
                    <h2 className="text-base font-semibold text-[var(--tower-text)]">
                      {activeMeta.name}
                    </h2>
                    <p className="text-xs text-[var(--tower-text-secondary)]">
                      {generated?.fileName ?? `${state?.snapshot.settings.configurationName || 'Tower'}.${activeMeta.fileExtension}`}
                    </p>
                  </div>
                </div>

                {/* Full Configuration vs Nodes-Only Segmented Control */}
                <div className="flex items-center gap-2">
                  {activeMeta.supportsFullConfiguration && activeMeta.supportsNodesOnly ? (
                    <div className="flex p-1 rounded-[12px] bg-[var(--tower-surface-subtle)] border border-[var(--tower-border)]">
                      <button
                        type="button"
                        onClick={() => void handleChangeMode('fullConfiguration')}
                        className={`px-3 py-1.5 rounded-[9px] text-xs font-semibold cursor-pointer ${
                          contentMode === 'fullConfiguration'
                            ? 'bg-[var(--tower-surface)] text-[var(--tower-text)] shadow-xs'
                            : 'text-[var(--tower-text-secondary)]'
                        }`}
                      >
                        完整配置
                      </button>
                      <button
                        type="button"
                        onClick={() => void handleChangeMode('nodesOnly')}
                        className={`px-3 py-1.5 rounded-[9px] text-xs font-semibold cursor-pointer ${
                          contentMode === 'nodesOnly'
                            ? 'bg-[var(--tower-surface)] text-[var(--tower-text)] shadow-xs'
                            : 'text-[var(--tower-text-secondary)]'
                        }`}
                      >
                        仅节点
                      </button>
                    </div>
                  ) : (
                    <span className="px-3 py-1.5 rounded-full bg-[var(--tower-surface-subtle)] text-xs font-medium text-[var(--tower-text-secondary)]">
                      {activeMeta.supportsFullConfiguration ? '仅完整配置' : '仅节点格式'}
                    </span>
                  )}

                  <button
                    type="button"
                    onClick={() => setProtocolModalOpen(true)}
                    className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-[11px] border text-xs font-medium cursor-pointer ${
                      excludedKindsForTarget.length > 0
                        ? 'bg-[var(--tower-accent-soft)] border-[var(--tower-accent)]/30 text-[var(--tower-accent)]'
                        : 'bg-[var(--tower-surface-subtle)] border-[var(--tower-border)] text-[var(--tower-text-secondary)] hover:text-[var(--tower-text)]'
                    }`}
                  >
                    <Filter className="w-3.5 h-3.5" />
                    <span>
                      协议筛选
                      {excludedKindsForTarget.length > 0 ? ` (-${excludedKindsForTarget.length})` : ''}
                    </span>
                  </button>
                </div>
              </div>

              {/* Compatibility Summary */}
              {generated && (
                <div className="flex flex-wrap items-center gap-2 text-xs">
                  <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 font-semibold tabular-nums">
                    <CheckCircle2 className="w-3.5 h-3.5" />
                    <span>支持导出 {generated.supportedCount} 个节点</span>
                  </span>

                  {generated.skippedCount > 0 && (
                    <button
                      type="button"
                      onClick={() => setSkippedModalOpen(true)}
                      className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-amber-500/12 text-amber-700 dark:text-amber-300 font-semibold tabular-nums hover:opacity-85 cursor-pointer"
                    >
                      <AlertTriangle className="w-3.5 h-3.5" />
                      <span>跳过 {generated.skippedCount} 个不支持节点（点击查看原因）</span>
                    </button>
                  )}

                  {generated.remoteSourceCount > 0 && (
                    <span className="px-2.5 py-1 rounded-full bg-[var(--tower-surface-subtle)] text-[var(--tower-text-secondary)] tabular-nums">
                      含 {generated.remoteSourceCount} 个内嵌远端订阅
                    </span>
                  )}
                </div>
              )}

              {/* Prominent Warnings Banner */}
              {generated?.warnings && generated.warnings.length > 0 && (
                <div className="p-3.5 rounded-[14px] bg-amber-500/10 border border-amber-500/25 space-y-1">
                  {generated.warnings.map((w, idx) => (
                    <div
                      key={idx}
                      className="flex items-start gap-2 text-xs text-amber-800 dark:text-amber-200 leading-relaxed"
                    >
                      <AlertTriangle className="w-3.5 h-3.5 shrink-0 mt-0.5 text-amber-500" />
                      <span>{w}</span>
                    </div>
                  ))}
                </div>
              )}

              {/* Quantumult X Full Config URL Scheme Boundary Note (CLAUDE.md #4) */}
              {isQuanxFull && (
                <div className="flex items-start gap-2.5 p-3.5 rounded-[14px] bg-sky-500/10 border border-sky-500/20 text-xs text-sky-800 dark:text-sky-200 leading-relaxed">
                  <Info className="w-4 h-4 shrink-0 mt-0.5 text-sky-500" />
                  <span>
                    Quantumult X 官方 URL Scheme 不支持直接导入本地完整配置（<code className="font-mono">[policy]</code> 策略组无法通过 Scheme 写入）。请直接下载配置文件，或使用「局域网 / 订阅共享」导入。
                  </span>
                </div>
              )}

              {/* Primary Actions */}
              <div className="flex flex-wrap items-center gap-2.5 pt-1">
                <a
                  href={api.downloadUrl(activeMeta.id, contentMode)}
                  download={generated?.fileName}
                  className="inline-flex items-center gap-2 px-5 py-2.5 rounded-[14px] bg-[var(--tower-accent)] text-white text-sm font-semibold hover:opacity-92 active:scale-[0.99] transition-all"
                >
                  <Download className="w-4 h-4" />
                  <span>下载配置文件</span>
                </a>

                <button
                  type="button"
                  onClick={() => void handleCopyConfig()}
                  disabled={!generated?.content}
                  className="inline-flex items-center gap-2 px-4 py-2.5 rounded-[14px] bg-[var(--tower-surface-subtle)] border border-[var(--tower-border)] text-sm font-semibold text-[var(--tower-text)] hover:bg-[var(--tower-border)] disabled:opacity-45 cursor-pointer"
                >
                  <Copy className="w-4 h-4" />
                  <span>复制配置文本</span>
                </button>

                {canShowDirectImport && generated?.directImportURL && (
                  <a
                    href={generated.directImportURL}
                    className="inline-flex items-center gap-2 px-4 py-2.5 rounded-[14px] bg-emerald-500/12 text-emerald-700 dark:text-emerald-300 border border-emerald-500/25 text-sm font-semibold hover:opacity-90"
                  >
                    <ExternalLink className="w-4 h-4" />
                    <span>一键导入到 {activeMeta.name}</span>
                  </a>
                )}
              </div>
            </div>

            {/* Configuration Preview */}
            {generating ? (
              <div className="tower-card py-16 flex items-center justify-center gap-2.5 text-sm text-[var(--tower-text-secondary)]">
                <Loader2 className="w-5 h-5 animate-spin text-[var(--tower-accent)]" />
                <span>正在生成 {activeMeta.name} 配置…</span>
              </div>
            ) : genError ? (
              <div className="tower-card p-6 text-center text-sm text-red-500">{genError}</div>
            ) : generated ? (
              <ConfigurationViewer
                content={generated.content}
                fileExtension={generated.fileExtension}
              />
            ) : null}
          </div>
        )
      )}

      {/* Client Picker Filter & Sort Modal */}
      <ClientPickerFilterModal open={filterModalOpen} onClose={() => setFilterModalOpen(false)} />

      {/* Skipped Reasons Modal */}
      <Modal
        open={skippedModalOpen}
        onClose={() => setSkippedModalOpen(false)}
        title="已跳过的不支持节点"
        subtitle={`${activeMeta?.name ?? ''} 无法忠实表达以下节点，已自动过滤`}
        maxWidthClass="max-w-md"
      >
        <div className="divide-y divide-[var(--tower-border)]">
          {(generated?.skippedReasons ?? []).map((item, idx) => (
            <div key={idx} className="py-2.5 flex items-center justify-between gap-3 text-sm">
              <span className="text-[var(--tower-text)]">{item.reason}</span>
              <span className="shrink-0 px-2 py-0.5 rounded-full bg-[var(--tower-surface-subtle)] text-xs font-semibold tabular-nums text-[var(--tower-text-secondary)]">
                {item.count} 个
              </span>
            </div>
          ))}
        </div>
      </Modal>

      {/* Protocol Exclusion Modal */}
      <Modal
        open={protocolModalOpen}
        onClose={() => setProtocolModalOpen(false)}
        title={`导出协议筛选 · ${activeMeta?.name ?? ''}`}
        subtitle="取消勾选的协议将在当前客户端导出时自动跳过"
        maxWidthClass="max-w-md"
      >
        <div className="grid grid-cols-2 gap-2">
          {ALL_PROXY_KINDS.map((kind) => {
            const clientSupports = activeMeta?.supportedKinds.includes(kind) ?? true;
            const included = clientSupports && !excludedKindsForTarget.includes(kind);
            return (
              <button
                key={kind}
                type="button"
                disabled={!clientSupports}
                onClick={() => void handleToggleExcludedKind(kind)}
                className={`flex items-center gap-2.5 px-3 py-2 rounded-[12px] border text-left text-xs font-medium transition-colors ${
                  !clientSupports
                    ? 'opacity-40 cursor-not-allowed bg-[var(--tower-surface-subtle)] border-[var(--tower-border)]'
                    : included
                      ? 'bg-[var(--tower-surface)] border-[var(--tower-border-strong)] cursor-pointer'
                      : 'bg-[var(--tower-surface-subtle)] border-[var(--tower-border)] opacity-65 cursor-pointer'
                }`}
              >
                <SelectionIndicator selected={included} size="sm" />
                <span className="truncate text-[var(--tower-text)]">
                  {PROXY_KIND_LABELS[kind] ?? kind}
                </span>
              </button>
            );
          })}
        </div>
      </Modal>
    </div>
  );
}
