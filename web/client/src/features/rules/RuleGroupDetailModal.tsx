import { useEffect, useMemo, useState } from 'react';
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
import { GripVertical, Plus, Trash2 } from 'lucide-react';
import { api } from '@/api/client';
import { Modal } from '@/components/Modal';
import { SelectionIndicator } from '@/components/SelectionIndicator';
import { useTower } from '@/state/TowerContext';
import type {
  RuleGroupPresentation,
  RuleSchemeGroupKind,
  RuleSchemeGroupMember,
} from '@shared/index';

const GROUP_KIND_OPTIONS: Array<{ value: RuleSchemeGroupKind; label: string }> = [
  { value: 'select', label: '手动选择 (select)' },
  { value: 'url-test', label: '自动测速 (url-test)' },
  { value: 'fallback', label: '故障转移 (fallback)' },
  { value: 'load-balance', label: '负载均衡 (load-balance)' },
];

function SortableMemberRow({
  name,
  selected,
  onToggle,
}: {
  name: string;
  selected: boolean;
  onToggle: () => void;
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: name,
  });
  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
  };

  return (
    <div
      ref={setNodeRef}
      style={style}
      className={`flex items-center gap-2.5 px-3 py-2 rounded-[11px] border text-sm ${
        selected
          ? 'bg-[var(--tower-surface)] border-[var(--tower-border-strong)]'
          : 'bg-[var(--tower-surface-subtle)]/60 border-[var(--tower-border)] opacity-65'
      } ${isDragging ? 'shadow-lg z-10' : ''}`}
    >
      <button
        type="button"
        {...attributes}
        {...listeners}
        aria-label={`拖动排序 ${name}`}
        className="text-[var(--tower-text-tertiary)] hover:text-[var(--tower-text)] cursor-grab active:cursor-grabbing p-0.5"
      >
        <GripVertical className="w-4 h-4" />
      </button>
      <SelectionIndicator selected={selected} onToggle={onToggle} size="sm" label={name} />
      <span className="flex-1 truncate font-medium text-[var(--tower-text)]">{name}</span>
    </div>
  );
}

interface RuleGroupDetailModalProps {
  schemeId: string;
  group: RuleGroupPresentation | null;
  onClose: () => void;
}

export function RuleGroupDetailModal({
  schemeId,
  group,
  onClose,
}: RuleGroupDetailModalProps) {
  const { state, applyStateFromMutation, showToast } = useTower();

  const [name, setName] = useState('');
  const [kind, setKind] = useState<RuleSchemeGroupKind>('select');
  const [orderedCandidates, setOrderedCandidates] = useState<string[]>([]);
  const [selectedRefs, setSelectedRefs] = useState<Set<string>>(new Set());
  const [nodePatternText, setNodePatternText] = useState('');
  const [supplementText, setSupplementText] = useState('');
  const [isEnabled, setIsEnabled] = useState(true);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [saving, setSaving] = useState(false);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  );

  const allCandidateTargets = useMemo(() => {
    if (!group || !state) return [];
    const base = state.activeSchemeRoutingTargets ?? [];
    const extra = state.activeSchemeAdditionalTargets?.[group.sourceName] ?? [];
    const seen = new Set<string>();
    const list: string[] = [];
    for (const item of [...base, ...extra]) {
      if (!seen.has(item)) {
        seen.add(item);
        list.push(item);
      }
    }
    return list;
  }, [group, state]);

  useEffect(() => {
    if (!group) return;
    setName(group.displayName);
    setKind(group.kind);
    setIsEnabled(group.isEnabled);
    setSupplementText(group.supplementRules.join('\n'));
    setConfirmDelete(false);

    const refMembers = group.members
      .filter((m): m is { type: 'reference'; name: string } => m.type === 'reference')
      .map((m) => m.name);
    const patternMembers = group.members
      .filter((m): m is { type: 'nodePattern'; pattern: string } => m.type === 'nodePattern')
      .map((m) => m.pattern);

    setNodePatternText(patternMembers.join('|'));
    setSelectedRefs(new Set(refMembers));

    // Preserve current member order first, then append remaining candidates
    const seen = new Set<string>();
    const ordered: string[] = [];
    for (const r of refMembers) {
      if (!seen.has(r)) {
        seen.add(r);
        ordered.push(r);
      }
    }
    for (const c of allCandidateTargets) {
      if (!seen.has(c) && c !== group.sourceName && c !== group.displayName) {
        seen.add(c);
        ordered.push(c);
      }
    }
    setOrderedCandidates(ordered);
  }, [allCandidateTargets, group]);

  const handleDragMembers = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    setOrderedCandidates((prev) => {
      const oldIndex = prev.indexOf(String(active.id));
      const newIndex = prev.indexOf(String(over.id));
      if (oldIndex < 0 || newIndex < 0) return prev;
      return arrayMove(prev, oldIndex, newIndex);
    });
  };

  const toggleMember = (memberName: string) => {
    setSelectedRefs((prev) => {
      const next = new Set(prev);
      if (next.has(memberName)) next.delete(memberName);
      else next.add(memberName);
      return next;
    });
  };

  const handleSave = async () => {
    if (!group) return;
    setSaving(true);
    try {
      const members: RuleSchemeGroupMember[] = [];
      if (group.editorMode === 'routingTargets') {
        for (const candidate of orderedCandidates) {
          if (selectedRefs.has(candidate)) {
            members.push({ type: 'reference', name: candidate });
          }
        }
      }
      if (group.isNodeFilterGroup || group.editorMode === 'nodePatternsOnly' || nodePatternText.trim()) {
        if (nodePatternText.trim()) {
          members.push({ type: 'nodePattern', pattern: nodePatternText.trim() });
        }
      }

      const supplementRules = supplementText
        .split('\n')
        .map((l) => l.trim())
        .filter(Boolean);

      const res = await api.updateRuleGroup(schemeId, group.sourceName, {
        newName: !group.isProtected && name.trim() ? name.trim() : undefined,
        kind,
        members,
        nodePattern: nodePatternText.trim() || undefined,
        supplementRules,
        isEnabled,
      });
      await applyStateFromMutation(res.state);
      onClose();
      showToast(`已更新策略组「${name.trim() || group.displayName}」`, 'success');
    } catch (err) {
      showToast(err instanceof Error ? err.message : '更新策略组失败', 'danger');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!group) return;
    setSaving(true);
    try {
      const res = await api.deleteRuleGroup(schemeId, group.sourceName);
      await applyStateFromMutation(res.state);
      onClose();
      showToast(`已删除策略组「${group.displayName}」`, 'neutral');
    } catch (err) {
      showToast(err instanceof Error ? err.message : '删除策略组失败', 'danger');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={group !== null}
      onClose={onClose}
      title={group ? `策略组 · ${group.displayName}` : '策略组详情'}
      subtitle={group?.isProtected ? '核心策略组（受保护，不可删除）' : undefined}
      maxWidthClass="max-w-xl"
      footer={
        group ? (
          <div className="w-full flex items-center justify-between gap-2">
            <div>
              {!group.isProtected && !confirmDelete && (
                <button
                  type="button"
                  onClick={() => setConfirmDelete(true)}
                  className="inline-flex items-center gap-1.5 px-3 py-2 rounded-[12px] text-xs font-semibold text-red-500 hover:bg-red-500/10 cursor-pointer"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                  <span>删除分组</span>
                </button>
              )}
              {!group.isProtected && confirmDelete && (
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => void handleDelete()}
                    disabled={saving}
                    className="px-3 py-1.5 rounded-[10px] bg-red-500 text-white text-xs font-semibold hover:bg-red-600 cursor-pointer"
                  >
                    确认删除
                  </button>
                  <button
                    type="button"
                    onClick={() => setConfirmDelete(false)}
                    className="px-2.5 py-1.5 text-xs text-[var(--tower-text-secondary)] cursor-pointer"
                  >
                    取消
                  </button>
                </div>
              )}
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={onClose}
                className="px-4 py-2 rounded-[12px] text-sm font-medium text-[var(--tower-text-secondary)] hover:text-[var(--tower-text)] cursor-pointer"
              >
                取消
              </button>
              <button
                type="button"
                onClick={() => void handleSave()}
                disabled={saving}
                className="px-5 py-2 rounded-[12px] bg-[var(--tower-accent)] text-white text-sm font-semibold hover:opacity-92 disabled:opacity-50 cursor-pointer"
              >
                {saving ? '保存中…' : '保存修改'}
              </button>
            </div>
          </div>
        ) : undefined
      }
    >
      {group && (
        <div className="space-y-5">
          {/* Enable Switch */}
          <div className="flex items-center justify-between p-3.5 rounded-[14px] bg-[var(--tower-surface-subtle)] border border-[var(--tower-border)]">
            <div>
              <div className="text-sm font-semibold text-[var(--tower-text)]">启用此策略组</div>
              <div className="text-xs text-[var(--tower-text-secondary)]">
                停用后将从导出配置与分流规则中省略
              </div>
            </div>
            <SelectionIndicator
              selected={isEnabled}
              onToggle={() => setIsEnabled((v) => !v)}
              label="启用策略组"
            />
          </div>

          {/* Name & Kind */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-[var(--tower-text-secondary)] mb-1.5">
                显示名称
              </label>
              <input
                type="text"
                value={name}
                disabled={group.isProtected}
                onChange={(e) => setName(e.target.value)}
                className="w-full px-3.5 py-2 text-sm tower-input disabled:opacity-60"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-[var(--tower-text-secondary)] mb-1.5">
                策略类型
              </label>
              <select
                value={kind}
                onChange={(e) => setKind(e.target.value as RuleSchemeGroupKind)}
                className="w-full px-3.5 py-2 text-sm tower-input"
              >
                {GROUP_KIND_OPTIONS.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* Node Filter Pattern */}
          {(group.isNodeFilterGroup || group.editorMode === 'nodePatternsOnly') && (
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label className="text-xs font-medium text-[var(--tower-text-secondary)]">
                  节点名称正则筛选
                </label>
                <span className="text-xs font-medium text-[var(--tower-accent)] tabular-nums">
                  当前匹配 {group.matchedNodeCount} 个节点
                </span>
              </div>
              <input
                type="text"
                value={nodePatternText}
                onChange={(e) => setNodePatternText(e.target.value)}
                placeholder="例如：(香港|HK|Hong Kong)"
                className="w-full px-3.5 py-2 text-sm tower-input font-mono"
              />
            </div>
          )}

          {/* Routing Target Candidates */}
          {group.editorMode === 'routingTargets' && (
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <label className="text-xs font-medium text-[var(--tower-text-secondary)]">
                  策略候选成员（勾选并拖拽排序）
                </label>
                <span className="text-xs text-[var(--tower-text-tertiary)] tabular-nums">
                  已选 {selectedRefs.size} 项
                </span>
              </div>
              <div className="max-h-56 overflow-y-auto space-y-1.5 pr-1">
                <DndContext
                  sensors={sensors}
                  collisionDetection={closestCenter}
                  onDragEnd={handleDragMembers}
                >
                  <SortableContext
                    items={orderedCandidates}
                    strategy={verticalListSortingStrategy}
                  >
                    {orderedCandidates.map((cand) => (
                      <SortableMemberRow
                        key={cand}
                        name={cand}
                        selected={selectedRefs.has(cand)}
                        onToggle={() => toggleMember(cand)}
                      />
                    ))}
                  </SortableContext>
                </DndContext>
              </div>
            </div>
          )}

          {/* Supplement Rules */}
          <div>
            <div className="flex items-center justify-between mb-1.5">
              <label className="text-xs font-medium text-[var(--tower-text-secondary)] flex items-center gap-1">
                <Plus className="w-3.5 h-3.5 text-[var(--tower-accent)]" />
                <span>我添加的规则（优先于方案规则匹配，每行一条）</span>
              </label>
              <span className="text-[11px] text-[var(--tower-text-tertiary)]">
                原方案含 {group.ruleCount} 条规则
              </span>
            </div>
            <textarea
              rows={4}
              value={supplementText}
              onChange={(e) => setSupplementText(e.target.value)}
              placeholder={'DOMAIN-SUFFIX,example.com\nDOMAIN-KEYWORD,openai\nIP-CIDR,1.1.1.1/32,no-resolve'}
              className="w-full p-3 text-xs tower-input font-mono leading-relaxed"
            />
          </div>
        </div>
      )}
    </Modal>
  );
}
