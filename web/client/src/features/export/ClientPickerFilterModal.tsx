import { useEffect, useState } from 'react';
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
import { GripVertical, Wifi } from 'lucide-react';
import { api } from '@/api/client';
import { ClientIcon } from '@/components/ClientIcon';
import { Modal } from '@/components/Modal';
import { SelectionIndicator } from '@/components/SelectionIndicator';
import { useTower } from '@/state/TowerContext';
import {
  CLIENT_TARGETS,
  DEFAULT_CLIENT_ORDER,
  type ClientTarget,
} from '@shared/index';

function SortableClientRow({
  id,
  visible,
  onToggle,
}: {
  id: string;
  visible: boolean;
  onToggle: () => void;
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id,
  });
  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
  };

  const isLan = id === '__lan__';
  const meta = !isLan ? CLIENT_TARGETS[id as ClientTarget] : null;

  return (
    <div
      ref={setNodeRef}
      style={style}
      className={`flex items-center gap-3 px-3 py-2.5 rounded-[13px] border ${
        visible
          ? 'bg-[var(--tower-surface)] border-[var(--tower-border)]'
          : 'bg-[var(--tower-surface-subtle)]/50 border-[var(--tower-border)] opacity-60'
      } ${isDragging ? 'shadow-lg z-20' : ''}`}
    >
      <button
        type="button"
        {...attributes}
        {...listeners}
        aria-label="拖动排序"
        className="p-1 text-[var(--tower-text-tertiary)] hover:text-[var(--tower-text)] cursor-grab active:cursor-grabbing touch-none"
      >
        <GripVertical className="w-4 h-4" />
      </button>

      <SelectionIndicator
        selected={visible}
        onToggle={onToggle}
        size="sm"
        label={isLan ? '局域网共享' : meta?.name ?? id}
      />

      {isLan ? (
        <div className="w-8 h-8 rounded-[9px] bg-[var(--tower-accent-soft)] text-[var(--tower-accent)] flex items-center justify-center shrink-0">
          <Wifi className="w-4 h-4" />
        </div>
      ) : (
        meta && (
          <ClientIcon
            iconFile={meta.iconFile}
            name={meta.name}
            brandColorHex={meta.brandColorHex}
            className="w-8 h-8"
          />
        )
      )}

      <div className="flex-1 min-w-0">
        <div className="text-sm font-semibold text-[var(--tower-text)] truncate">
          {isLan ? '局域网 / 订阅共享' : meta?.name ?? id}
        </div>
        <div className="text-[11px] text-[var(--tower-text-secondary)] truncate">
          {isLan ? '二维码与通用订阅链接' : meta?.subtitle}
        </div>
      </div>
    </div>
  );
}

interface ClientPickerFilterModalProps {
  open: boolean;
  onClose: () => void;
}

export function ClientPickerFilterModal({ open, onClose }: ClientPickerFilterModalProps) {
  const { state, applyStateFromMutation, showToast } = useTower();
  const [items, setItems] = useState<string[]>([]);
  const [visibleSet, setVisibleSet] = useState<Set<string>>(new Set());
  const [saving, setSaving] = useState(false);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  );

  useEffect(() => {
    if (!open || !state) return;
    const baseOrder =
      state.snapshot.clientOrder.length > 0 ? [...state.snapshot.clientOrder] : [...DEFAULT_CLIENT_ORDER];
    // Append any missing targets
    for (const t of DEFAULT_CLIENT_ORDER) {
      if (!baseOrder.includes(t)) baseOrder.push(t);
    }
    const combined: string[] = [...baseOrder];
    const lanIdx = Math.min(Math.max(0, state.snapshot.lanSharingOrderIndex ?? 0), combined.length);
    combined.splice(lanIdx, 0, '__lan__');

    setItems(combined);
    const vis = new Set<string>(state.snapshot.visibleClientTargets);
    if (state.snapshot.isLANSharingVisible !== false) {
      vis.add('__lan__');
    }
    setVisibleSet(vis);
  }, [open, state]);

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    setItems((prev) => {
      const oldIdx = prev.indexOf(String(active.id));
      const newIdx = prev.indexOf(String(over.id));
      if (oldIdx < 0 || newIdx < 0) return prev;
      return arrayMove(prev, oldIdx, newIdx);
    });
  };

  const toggleVisible = (id: string) => {
    setVisibleSet((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        // Keep at least 1 client visible
        if (next.size <= 1) return prev;
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      const lanIndex = Math.max(0, items.indexOf('__lan__'));
      const clientOrder = items.filter((id) => id !== '__lan__') as ClientTarget[];
      const visibleClientTargets = clientOrder.filter((id) => visibleSet.has(id));
      const isLANSharingVisible = visibleSet.has('__lan__');

      const res = await api.updateExportPreferences({
        clientOrder,
        visibleClientTargets,
        lanSharingOrderIndex: lanIndex,
        isLANSharingVisible,
      });
      await applyStateFromMutation(res.state);
      onClose();
      showToast('已更新客户端排序与可见性', 'success');
    } catch (err) {
      showToast(err instanceof Error ? err.message : '保存失败', 'danger');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="筛选与排序客户端"
      subtitle="勾选要在导出栏显示的客户端，拖动左侧手柄调整先后顺序"
      footer={
        <>
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
            {saving ? '保存中…' : '完成'}
          </button>
        </>
      }
    >
      <div className="max-h-96 overflow-y-auto space-y-1.5 pr-1">
        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
          <SortableContext items={items} strategy={verticalListSortingStrategy}>
            {items.map((id) => (
              <SortableClientRow
                key={id}
                id={id}
                visible={visibleSet.has(id)}
                onToggle={() => toggleVisible(id)}
              />
            ))}
          </SortableContext>
        </DndContext>
      </div>
    </Modal>
  );
}
