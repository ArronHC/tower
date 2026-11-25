import { useEffect, useState } from 'react';
import { BookOpen, Loader2, Plus, Search } from 'lucide-react';
import { api } from '@/api/client';
import { Modal } from '@/components/Modal';
import { useTower } from '@/state/TowerContext';
import type { RuleCatalogEntry } from '@shared/index';

interface RuleCatalogModalProps {
  open: boolean;
  schemeId: string;
  onClose: () => void;
}

export function RuleCatalogModal({ open, schemeId, onClose }: RuleCatalogModalProps) {
  const { applyStateFromMutation, showToast } = useTower();
  const [query, setQuery] = useState('');
  const [source, setSource] = useState<'all' | 'acl4ssr' | 'blackmatrix7'>('all');
  const [entries, setEntries] = useState<RuleCatalogEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [addingId, setAddingId] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setLoading(true);
    const timer = window.setTimeout(async () => {
      try {
        const res = await api.searchRuleCatalog({ q: query.trim(), source, limit: 50 });
        if (cancelled) return;
        const list = Array.isArray(res) ? res : res.items ?? res.entries ?? [];
        setEntries(list);
      } catch {
        if (!cancelled) setEntries([]);
      } finally {
        if (!cancelled) setLoading(false);
      }
    }, 220);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [open, query, source]);

  const handleAddEntry = async (entry: RuleCatalogEntry) => {
    setAddingId(entry.id);
    try {
      const policyName = `${entry.emoji ? `${entry.emoji} ` : ''}${entry.displayName}`.trim();
      const res = await api.createRuleFlow({
        schemeID: schemeId,
        name: entry.displayName,
        policyName,
        catalogID: entry.id,
        sourceURLString: entry.url,
        isEnabled: true,
      });
      await applyStateFromMutation(res.state);
      showToast(`已添加「${entry.displayName}」到当前规则方案`, 'success');
    } catch (err) {
      showToast(err instanceof Error ? err.message : '添加规则库条目失败', 'danger');
    } finally {
      setAddingId(null);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="内置规则库搜索"
      subtitle="离线索引 ACL4SSR 与 blackmatrix7 分流规则条目"
      maxWidthClass="max-w-2xl"
    >
      <div className="space-y-3.5">
        <div className="flex flex-wrap items-center gap-2">
          <div className="relative flex-1 min-w-[200px]">
            <Search className="w-4 h-4 text-[var(--tower-text-tertiary)] absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="搜索服务名称，如 OpenAI, Netflix, Steam, Telegram…"
              className="w-full pl-9 pr-3 py-2 text-sm tower-input"
            />
          </div>

          <div className="flex p-1 rounded-[11px] bg-[var(--tower-surface-subtle)] border border-[var(--tower-border)]">
            {(['all', 'acl4ssr', 'blackmatrix7'] as const).map((src) => (
              <button
                key={src}
                type="button"
                onClick={() => setSource(src)}
                className={`px-2.5 py-1 rounded-[8px] text-xs font-medium transition-colors cursor-pointer ${
                  source === src
                    ? 'bg-[var(--tower-surface)] text-[var(--tower-text)] shadow-xs'
                    : 'text-[var(--tower-text-secondary)]'
                }`}
              >
                {src === 'all' ? '全部' : src}
              </button>
            ))}
          </div>
        </div>

        <div className="max-h-96 overflow-y-auto divide-y divide-[var(--tower-border)] rounded-[14px] border border-[var(--tower-border)]">
          {loading ? (
            <div className="py-12 flex items-center justify-center gap-2 text-sm text-[var(--tower-text-secondary)]">
              <Loader2 className="w-4 h-4 animate-spin" />
              <span>搜索规则库…</span>
            </div>
          ) : entries.length === 0 ? (
            <div className="py-12 text-center space-y-2">
              <BookOpen className="w-7 h-7 mx-auto text-[var(--tower-text-tertiary)]" />
              <p className="text-sm text-[var(--tower-text-secondary)]">未找到匹配的规则条目</p>
            </div>
          ) : (
            entries.map((entry) => (
              <div
                key={entry.id}
                className="flex items-center justify-between gap-3 px-3.5 py-2.5 hover:bg-[var(--tower-surface-subtle)]/60 transition-colors"
              >
                <div className="flex items-center gap-2.5 min-w-0">
                  <span className="text-lg leading-none shrink-0">{entry.emoji || '📌'}</span>
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-medium text-[var(--tower-text)] truncate">
                        {entry.displayName}
                      </span>
                      <span className="px-1.5 py-0.5 rounded-[6px] bg-[var(--tower-surface-subtle)] text-[10px] font-medium text-[var(--tower-text-secondary)]">
                        {entry.source}
                      </span>
                    </div>
                    <div className="text-[11px] text-[var(--tower-text-secondary)] truncate">
                      {entry.category} · {entry.name}
                    </div>
                  </div>
                </div>

                <button
                  type="button"
                  onClick={() => void handleAddEntry(entry)}
                  disabled={addingId === entry.id}
                  className="shrink-0 inline-flex items-center gap-1 px-3 py-1.5 rounded-full bg-[var(--tower-accent-soft)] text-[var(--tower-accent)] text-xs font-semibold hover:opacity-85 disabled:opacity-50 cursor-pointer"
                >
                  {addingId === entry.id ? (
                    <Loader2 className="w-3.5 h-3.5 animate-spin" />
                  ) : (
                    <Plus className="w-3.5 h-3.5" />
                  )}
                  <span>加入方案</span>
                </button>
              </div>
            ))
          )}
        </div>
      </div>
    </Modal>
  );
}
