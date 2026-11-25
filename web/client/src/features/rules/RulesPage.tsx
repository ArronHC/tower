import { useMemo, useState } from 'react';
import {
  DndContext,
  PointerSensor,
  KeyboardSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core';
import {
  SortableContext,
  arrayMove,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import {
  AlertTriangle,
  BookOpen,
  CheckCircle2,
  ChevronRight,
  FileCode2,
  FolderPlus,
  GripVertical,
  Plus,
  RefreshCw,
  Shield,
  Sparkles,
  Trash2,
  Undo2,
} from 'lucide-react';
import { api } from '@/api/client';
import { Modal } from '@/components/Modal';
import { SelectionIndicator } from '@/components/SelectionIndicator';
import { CreateGroupModal } from '@/features/rules/CreateGroupModal';
import { ImportSchemeModal } from '@/features/rules/ImportSchemeModal';
import { LocalRuleSetsModal } from '@/features/rules/LocalRuleSetsModal';
import { RuleCatalogModal } from '@/features/rules/RuleCatalogModal';
import { RuleGroupDetailModal } from '@/features/rules/RuleGroupDetailModal';
import { useTower } from '@/state/TowerContext';
import type {
  MatchingOrderConflict,
  RuleGroupPresentation,
  RuleSchemeCustomization,
  RuleSchemeSummary,
} from '@shared/index';

const KIND_BADGE: Record<string, string> = {
  select: '手动选择',
  'url-test': '自动测速',
  fallback: '故障转移',
  'load-balance': '负载均衡',
  smart: '智能优选',
  ssid: 'SSID',
  relay: '链式代理',
  unsupported: '未支持',
};

function SortablePolicyGroupRow({
  group,
  showEmoji,
  onToggleEnable,
  onOpenDetail,
}: {
  group: RuleGroupPresentation;
  showEmoji: boolean;
  onToggleEnable: () => void;
  onOpenDetail: () => void;
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: group.sourceName,
  });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
  };

  return (
    <div
      ref={setNodeRef}
      style={style}
      className={`flex items-center gap-3 px-3.5 py-3 rounded-[15px] border transition-colors ${
        isDragging
          ? 'bg-[var(--tower-surface)] border-[var(--tower-accent)] shadow-lg z-20'
          : 'bg-[var(--tower-surface)] border-[var(--tower-border)] hover:border-[var(--tower-border-strong)]'
      } ${group.isEnabled ? '' : 'opacity-60'}`}
    >
      <button
        type="button"
        {...attributes}
        {...listeners}
        aria-label={`拖拽排序 ${group.displayName}`}
        className="p-1 -ml-1 text-[var(--tower-text-tertiary)] hover:text-[var(--tower-text)] cursor-grab active:cursor-grabbing touch-none"
      >
        <GripVertical className="w-4 h-4" />
      </button>

      <SelectionIndicator
        selected={group.isEnabled}
        onToggle={onToggleEnable}
        label={group.isEnabled ? `停用 ${group.displayName}` : `启用 ${group.displayName}`}
        size="sm"
      />

      <button
        type="button"
        onClick={onOpenDetail}
        className="flex-1 min-w-0 flex items-center justify-between gap-3 text-left cursor-pointer group"
      >
        <div className="flex items-center gap-2.5 min-w-0">
          {showEmoji && group.emoji && (
            <span className="text-lg leading-none shrink-0">{group.emoji}</span>
          )}
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <span className="text-sm font-semibold text-[var(--tower-text)] truncate group-hover:text-[var(--tower-accent)] transition-colors">
                {group.displayName}
              </span>
              {group.isProtected && (
                <Shield className="w-3.5 h-3.5 text-[var(--tower-accent)] shrink-0" />
              )}
              {group.isNodeFilterGroup && (
                <span className="px-1.5 py-0.5 rounded-[6px] bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 text-[10px] font-medium shrink-0">
                  节点组 · {group.matchedNodeCount}
                </span>
              )}
            </div>
            <div className="mt-0.5 flex items-center gap-2 text-[11px] text-[var(--tower-text-secondary)]">
              <span>{KIND_BADGE[group.kind] ?? group.kind}</span>
              <span>· {group.members.length} 个成员</span>
              {group.ruleCount > 0 && <span>· {group.ruleCount} 条规则</span>}
              {group.supplementRules.length > 0 && (
                <span className="text-[var(--tower-accent)]">
                  · +{group.supplementRules.length} 自定义
                </span>
              )}
            </div>
          </div>
        </div>

        <ChevronRight className="w-4 h-4 text-[var(--tower-text-tertiary)] group-hover:text-[var(--tower-text)] shrink-0" />
      </button>
    </div>
  );
}

export function RulesPage() {
  const { state, applyStateFromMutation, reloadState, showToast } = useTower();

  const [importOpen, setImportOpen] = useState(false);
  const [createGroupOpen, setCreateGroupOpen] = useState(false);
  const [catalogOpen, setCatalogOpen] = useState(false);
  const [localSetsOpen, setLocalSetsOpen] = useState(false);
  const [detailGroupName, setDetailGroupName] = useState<string | null>(null);
  const [refreshingSchemeId, setRefreshingSchemeId] = useState<string | null>(null);

  // Conflict modal state
  const [conflictModal, setConflictModal] = useState<{
    schemeId: string;
    conflicts: MatchingOrderConflict[];
    previousCustomization: RuleSchemeCustomization | null;
  } | null>(null);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  );

  const selectedSchemeId = state?.snapshot.selectedPresetID ?? '';
  const allSchemes: RuleSchemeSummary[] = useMemo(
    () => [...(state?.bundledSchemes ?? []), ...(state?.importedSchemes ?? [])],
    [state?.bundledSchemes, state?.importedSchemes]
  );
  const activeScheme = allSchemes.find((s) => s.id === selectedSchemeId) ?? allSchemes[0];
  const groups = state?.activeSchemeGroups ?? [];

  const detailGroup = useMemo(
    () => groups.find((g) => g.sourceName === detailGroupName) ?? null,
    [detailGroupName, groups]
  );

  const handleSelectScheme = async (schemeId: string) => {
    if (schemeId === selectedSchemeId) return;
    try {
      const res = await api.selectRuleScheme(schemeId);
      await applyStateFromMutation(res.state);
    } catch (err) {
      showToast(err instanceof Error ? err.message : '切换规则方案失败', 'danger');
    }
  };

  const handleRefreshScheme = async (scheme: RuleSchemeSummary) => {
    setRefreshingSchemeId(scheme.id);
    try {
      const res = await api.refreshRuleScheme(scheme.id);
      await applyStateFromMutation(res.state);
      showToast(`已刷新规则方案「${scheme.name}」`, 'success');
    } catch (err) {
      showToast(err instanceof Error ? err.message : '刷新方案失败', 'danger');
    } finally {
      setRefreshingSchemeId(null);
    }
  };

  const handleDeleteScheme = async (scheme: RuleSchemeSummary) => {
    try {
      const res = await api.deleteRuleScheme(scheme.id);
      await applyStateFromMutation(res.state);
      showToast(`已删除导入方案「${scheme.name}」`, 'neutral');
    } catch (err) {
      showToast(err instanceof Error ? err.message : '删除方案失败', 'danger');
    }
  };

  const toggleEmojiSetting = async (field: 'emojiEnabled' | 'emojiAutofill') => {
    if (!activeScheme) return;
    const currentCustomization =
      state?.snapshot.ruleSchemeCustomizations[activeScheme.id] ?? {};
    const nextEnabled =
      field === 'emojiEnabled' ? !activeScheme.emojiEnabled : activeScheme.emojiEnabled;
    const nextAutofill =
      field === 'emojiAutofill' ? !activeScheme.emojiAutofill : activeScheme.emojiAutofill;

    try {
      const res = await api.updateSchemeCustomization(activeScheme.id, currentCustomization, {
        emojiEnabled: nextEnabled,
        emojiAutofill: nextAutofill,
      });
      await applyStateFromMutation(res.state);
    } catch (err) {
      showToast(err instanceof Error ? err.message : '更新图标设置失败', 'danger');
    }
  };

  const handleToggleGroupEnabled = async (group: RuleGroupPresentation) => {
    if (!activeScheme) return;
    const currentEnabled = groups.filter((g) => g.isEnabled).map((g) => g.sourceName);
    const nextEnabled = group.isEnabled
      ? currentEnabled.filter((n) => n !== group.sourceName)
      : [...currentEnabled, group.sourceName];

    try {
      const res = await api.setEnabledRuleGroups(activeScheme.id, nextEnabled);
      await applyStateFromMutation(res.state);
    } catch (err) {
      showToast(err instanceof Error ? err.message : '切换策略组失败', 'danger');
    }
  };

  const handleDragGroupsEnd = async (event: DragEndEvent) => {
    if (!activeScheme) return;
    const { active, over } = event;
    if (!over || active.id === over.id) return;

    const names = groups.map((g) => g.sourceName);
    const fromIndex = names.indexOf(String(active.id));
    const toIndex = names.indexOf(String(over.id));
    if (fromIndex < 0 || toIndex < 0) return;

    const orderedGroupNames = arrayMove(names, fromIndex, toIndex);

    try {
      const res = await api.reorderRuleGroups(activeScheme.id, {
        fromIndex,
        toIndex,
        orderedGroupNames,
      });
      await reloadState();
      if (res.conflicts && res.conflicts.length > 0) {
        setConflictModal({
          schemeId: activeScheme.id,
          conflicts: res.conflicts,
          previousCustomization: res.previousCustomization,
        });
      }
    } catch (err) {
      showToast(err instanceof Error ? err.message : '调整排序失败', 'danger');
    }
  };

  const handleRevertOrder = async () => {
    if (!conflictModal) return;
    try {
      const res = await api.updateSchemeCustomization(
        conflictModal.schemeId,
        conflictModal.previousCustomization ?? {}
      );
      await applyStateFromMutation(res.state);
      setConflictModal(null);
      showToast('已撤销排序变更并恢复原匹配顺序', 'neutral');
    } catch (err) {
      showToast(err instanceof Error ? err.message : '撤销失败', 'danger');
    }
  };

  return (
    <div className="space-y-6">
      {/* Schemes Section */}
      <section className="space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div>
            <h1 className="text-lg font-semibold tracking-tight text-[var(--tower-text)]">
              分流规则方案
            </h1>
            <p className="text-xs text-[var(--tower-text-secondary)]">
              内置 ACL4SSR 离线快照与用户导入方案，仅在您主动导入或刷新时联网
            </p>
          </div>
          <button
            type="button"
            onClick={() => setImportOpen(true)}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-[13px] bg-[var(--tower-accent)] text-white text-xs font-semibold hover:opacity-92 cursor-pointer"
          >
            <Plus className="w-4 h-4" />
            <span>导入方案</span>
          </button>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          {allSchemes.map((scheme) => {
            const isSelected = scheme.id === activeScheme?.id;
            const isRefreshing = refreshingSchemeId === scheme.id;
            return (
              <div
                key={scheme.id}
                onClick={() => void handleSelectScheme(scheme.id)}
                className={`tower-card-compact p-4 text-left transition-all cursor-pointer relative ${
                  isSelected
                    ? 'border-[var(--tower-accent)] ring-2 ring-[var(--tower-accent-soft)]'
                    : 'hover:border-[var(--tower-border-strong)]'
                }`}
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <div className="flex items-center gap-1.5">
                      <span className="font-semibold text-[15px] text-[var(--tower-text)] truncate">
                        {scheme.name}
                      </span>
                      {scheme.isBundled ? (
                        <span className="px-1.5 py-0.5 rounded-[6px] bg-[var(--tower-surface-subtle)] text-[10px] font-medium text-[var(--tower-text-secondary)] shrink-0">
                          内置
                        </span>
                      ) : (
                        <span className="px-1.5 py-0.5 rounded-[6px] bg-purple-500/10 text-[10px] font-medium text-purple-600 dark:text-purple-400 shrink-0">
                          导入
                        </span>
                      )}
                    </div>
                    <p className="mt-1 text-xs text-[var(--tower-text-secondary)] line-clamp-2">
                      {scheme.summary}
                    </p>
                  </div>

                  {isSelected && (
                    <CheckCircle2 className="w-5 h-5 text-[var(--tower-accent)] shrink-0" />
                  )}
                </div>

                <div className="mt-3 pt-2.5 border-t border-[var(--tower-border)] flex items-center justify-between text-[11px] text-[var(--tower-text-secondary)] tabular-nums">
                  <span>
                    {scheme.groupCount} 策略组 · {scheme.rulesetCount} 规则集
                  </span>
                  {!scheme.isBundled && (
                    <div
                      className="flex items-center gap-1"
                      onClick={(e) => e.stopPropagation()}
                    >
                      {scheme.sourceURLString && (
                        <button
                          type="button"
                          onClick={() => void handleRefreshScheme(scheme)}
                          disabled={isRefreshing}
                          title="刷新远程方案"
                          className="p-1 rounded-full text-[var(--tower-accent)] hover:bg-[var(--tower-accent-soft)] cursor-pointer"
                        >
                          <RefreshCw className={`w-3.5 h-3.5 ${isRefreshing ? 'animate-spin' : ''}`} />
                        </button>
                      )}
                      <button
                        type="button"
                        onClick={() => void handleDeleteScheme(scheme)}
                        title="删除方案"
                        className="p-1 rounded-full text-[var(--tower-text-secondary)] hover:text-red-500 hover:bg-red-500/10 cursor-pointer"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </section>

      {/* Active Scheme Controls & Unified Draggable Policy Groups */}
      {activeScheme && (
        <section className="tower-card p-4 sm:p-5 space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 className="text-[16px] font-semibold text-[var(--tower-text)]">
                策略组与分流优先级
              </h2>
              <p className="text-xs text-[var(--tower-text-secondary)]">
                拖动左侧手柄即可同步调整客户端面板顺序与匹配优先级（{groups.length} 组）
              </p>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              <button
                type="button"
                onClick={() => void toggleEmojiSetting('emojiEnabled')}
                className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium border transition-colors cursor-pointer ${
                  activeScheme.emojiEnabled
                    ? 'bg-[var(--tower-accent-soft)] border-[var(--tower-accent)]/30 text-[var(--tower-accent)]'
                    : 'bg-[var(--tower-surface-subtle)] border-[var(--tower-border)] text-[var(--tower-text-secondary)]'
                }`}
              >
                <Sparkles className="w-3.5 h-3.5" />
                <span>显示策略图标</span>
              </button>

              {activeScheme.emojiEnabled && (
                <button
                  type="button"
                  onClick={() => void toggleEmojiSetting('emojiAutofill')}
                  className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium border transition-colors cursor-pointer ${
                    activeScheme.emojiAutofill
                      ? 'bg-[var(--tower-accent-soft)] border-[var(--tower-accent)]/30 text-[var(--tower-accent)]'
                      : 'bg-[var(--tower-surface-subtle)] border-[var(--tower-border)] text-[var(--tower-text-secondary)]'
                  }`}
                >
                  <span>智能补全图标</span>
                </button>
              )}
            </div>
          </div>

          {/* Action Toolbar */}
          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={() => setCreateGroupOpen(true)}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-[12px] bg-[var(--tower-surface-subtle)] border border-[var(--tower-border)] text-xs font-semibold text-[var(--tower-text)] hover:bg-[var(--tower-border)] cursor-pointer"
            >
              <FolderPlus className="w-3.5 h-3.5 text-[var(--tower-accent)]" />
              <span>新建节点过滤分组</span>
            </button>

            <button
              type="button"
              onClick={() => setCatalogOpen(true)}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-[12px] bg-[var(--tower-surface-subtle)] border border-[var(--tower-border)] text-xs font-semibold text-[var(--tower-text)] hover:bg-[var(--tower-border)] cursor-pointer"
            >
              <BookOpen className="w-3.5 h-3.5 text-emerald-500" />
              <span>规则库搜索 (ACL4SSR / blackmatrix7)</span>
            </button>

            <button
              type="button"
              onClick={() => setLocalSetsOpen(true)}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-[12px] bg-[var(--tower-surface-subtle)] border border-[var(--tower-border)] text-xs font-semibold text-[var(--tower-text)] hover:bg-[var(--tower-border)] cursor-pointer"
            >
              <FileCode2 className="w-3.5 h-3.5 text-indigo-500" />
              <span>自定义规则流与本地规则集</span>
            </button>
          </div>

          {/* Unified Draggable List */}
          <DndContext
            sensors={sensors}
            collisionDetection={closestCenter}
            onDragEnd={(e) => void handleDragGroupsEnd(e)}
          >
            <SortableContext
              items={groups.map((g) => g.sourceName)}
              strategy={verticalListSortingStrategy}
            >
              <div className="space-y-2">
                {groups.map((group) => (
                  <SortablePolicyGroupRow
                    key={group.sourceName}
                    group={group}
                    showEmoji={activeScheme.emojiEnabled}
                    onToggleEnable={() => void handleToggleGroupEnabled(group)}
                    onOpenDetail={() => setDetailGroupName(group.sourceName)}
                  />
                ))}
              </div>
            </SortableContext>
          </DndContext>
        </section>
      )}

      {/* Modals */}
      <ImportSchemeModal open={importOpen} onClose={() => setImportOpen(false)} />

      {activeScheme && (
        <>
          <CreateGroupModal
            open={createGroupOpen}
            schemeId={activeScheme.id}
            onClose={() => setCreateGroupOpen(false)}
          />
          <RuleCatalogModal
            open={catalogOpen}
            schemeId={activeScheme.id}
            onClose={() => setCatalogOpen(false)}
          />
          <LocalRuleSetsModal
            open={localSetsOpen}
            schemeId={activeScheme.id}
            onClose={() => setLocalSetsOpen(false)}
          />
          <RuleGroupDetailModal
            schemeId={activeScheme.id}
            group={detailGroup}
            onClose={() => setDetailGroupName(null)}
          />
        </>
      )}

      {/* Matching Order Conflict Modal */}
      <Modal
        open={conflictModal !== null}
        onClose={() => setConflictModal(null)}
        title="匹配结果会改变"
        subtitle="本次拖拽调整了策略组之间的规则匹配先后顺序"
        maxWidthClass="max-w-lg"
        footer={
          <>
            <button
              type="button"
              onClick={() => void handleRevertOrder()}
              className="inline-flex items-center gap-1.5 px-4 py-2 rounded-[12px] bg-[var(--tower-surface-subtle)] border border-[var(--tower-border)] text-sm font-semibold text-[var(--tower-text)] hover:bg-[var(--tower-border)] cursor-pointer"
            >
              <Undo2 className="w-4 h-4" />
              <span>撤销</span>
            </button>
            <button
              type="button"
              onClick={() => setConflictModal(null)}
              className="px-5 py-2 rounded-[12px] bg-[var(--tower-accent)] text-white text-sm font-semibold hover:opacity-92 cursor-pointer"
            >
              保留修改
            </button>
          </>
        }
      >
        {conflictModal && (
          <div className="space-y-3">
            <div className="flex items-start gap-2.5 p-3 rounded-[13px] bg-amber-500/10 border border-amber-500/25 text-xs text-amber-700 dark:text-amber-300">
              <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
              <span>
                以下重叠域名将改由新的前置策略组优先命中，若不符合预期可立即撤销：
              </span>
            </div>

            <div className="space-y-2.5 max-h-64 overflow-y-auto">
              {conflictModal.conflicts.map((c, idx) => (
                <div
                  key={idx}
                  className="p-3 rounded-[12px] bg-[var(--tower-surface-subtle)] border border-[var(--tower-border)] space-y-1.5"
                >
                  <div className="text-xs font-semibold text-[var(--tower-text)]">
                    {c.direction === 'shadowedByMoved'
                      ? `「${c.movedGroup}」现在将优先于「${c.otherGroup}」匹配：`
                      : `「${c.otherGroup}」现在将优先于「${c.movedGroup}」匹配：`}
                  </div>
                  <div className="flex flex-wrap gap-1.5">
                    {c.sampleDomains.map((dom) => (
                      <span
                        key={dom}
                        className="px-2 py-0.5 rounded-[6px] bg-[var(--tower-surface)] border border-[var(--tower-border)] font-mono text-[11px] text-[var(--tower-text-secondary)]"
                      >
                        {dom}
                      </span>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
