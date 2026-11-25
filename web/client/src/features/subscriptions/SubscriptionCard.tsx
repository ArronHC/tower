import { useState } from 'react';
import {
  AlertCircle,
  ChevronDown,
  ChevronRight,
  Clock,
  Edit3,
  RefreshCw,
  Trash2,
} from 'lucide-react';
import { api } from '@/api/client';
import { Modal } from '@/components/Modal';
import { SelectionIndicator } from '@/components/SelectionIndicator';
import { useTower } from '@/state/TowerContext';
import type { ProxyNode, SubscriptionSource } from '@shared/index';

function formatBytes(bytes: number): string {
  if (bytes <= 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let idx = 0;
  let val = bytes;
  while (val >= 1024 && idx < units.length - 1) {
    val /= 1024;
    idx++;
  }
  return `${val >= 100 || idx === 0 ? val.toFixed(0) : val.toFixed(2)} ${units[idx]}`;
}

function formatExpiry(expiresAt: string): { label: string; expired: boolean; soon: boolean } {
  const date = new Date(expiresAt);
  if (Number.isNaN(date.getTime())) {
    return { label: expiresAt, expired: false, soon: false };
  }
  const diffMs = date.getTime() - Date.now();
  const days = Math.ceil(diffMs / 86400000);
  if (days < 0) return { label: `已过期 ${Math.abs(days)} 天`, expired: true, soon: false };
  if (days === 0) return { label: '今日到期', expired: false, soon: true };
  if (days <= 7) return { label: `剩余 ${days} 天`, expired: false, soon: true };
  return { label: `${date.toLocaleDateString('zh-CN')} 到期 (${days} 天)`, expired: false, soon: false };
}

function extractHost(urlString: string): string {
  try {
    return new URL(urlString).host;
  } catch {
    return urlString;
  }
}

interface SubscriptionCardProps {
  subscription: SubscriptionSource;
  nodes: ProxyNode[];
}

export function SubscriptionCard({ subscription, nodes }: SubscriptionCardProps) {
  const {
    state,
    refreshingSubIds,
    refreshSingleSubscription,
    applyStateFromMutation,
    showToast,
  } = useTower();

  const [expanded, setExpanded] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [saving, setSaving] = useState(false);

  // Edit form fields
  const [editName, setEditName] = useState(subscription.name);
  const [editUrl, setEditUrl] = useState(subscription.urlString);
  const [editUA, setEditUA] = useState(subscription.requestOptions?.userAgent ?? '');
  const [editDoH, setEditDoH] = useState(subscription.requestOptions?.dnsOverHTTPSURL ?? '');

  const isRefreshing = refreshingSubIds.has(subscription.id);
  const excludedSet = new Set(state?.snapshot.excludedNodeIDs ?? []);
  const activeCount = nodes.filter((n) => !n.isSubscriptionMetadata && !excludedSet.has(n.id)).length;
  const totalCount = nodes.filter((n) => !n.isSubscriptionMetadata).length;

  const usage = subscription.usage;
  const usedBytes = (usage?.uploadBytes ?? 0) + (usage?.downloadBytes ?? 0);
  const totalBytes = usage?.totalBytes ?? 0;
  const hasTraffic = totalBytes > 0;
  const usedPercent = hasTraffic ? Math.min(100, Math.round((usedBytes / totalBytes) * 100)) : 0;
  const remainingBytes = hasTraffic ? Math.max(0, totalBytes - usedBytes) : 0;
  const expiry = usage?.expiresAt ? formatExpiry(usage.expiresAt) : null;

  const toggleEnabled = async () => {
    try {
      const res = await api.updateSubscription(subscription.id, {
        isEnabled: !subscription.isEnabled,
      });
      await applyStateFromMutation('state' in res ? res.state : undefined);
    } catch (err) {
      showToast(err instanceof Error ? err.message : '切换订阅状态失败', 'danger');
    }
  };

  const handleSaveEdit = async () => {
    if (!editUrl.trim()) {
      showToast('订阅地址不能为空', 'warning');
      return;
    }
    setSaving(true);
    try {
      const res = await api.updateSubscription(subscription.id, {
        name: editName.trim() || subscription.name,
        urlString: editUrl.trim(),
        requestOptions:
          editUA.trim() || editDoH.trim()
            ? {
                userAgent: editUA.trim() || null,
                dnsOverHTTPSURL: editDoH.trim() || null,
              }
            : null,
      });
      await applyStateFromMutation('state' in res ? res.state : undefined);
      setEditOpen(false);
      showToast('订阅设置已保存', 'success');
    } catch (err) {
      showToast(err instanceof Error ? err.message : '保存订阅失败', 'danger');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    setSaving(true);
    try {
      const res = await api.deleteSubscription(subscription.id);
      await applyStateFromMutation('state' in res ? res.state : undefined);
      setConfirmDelete(false);
      showToast(`已删除订阅「${subscription.name}」`, 'neutral');
    } catch (err) {
      showToast(err instanceof Error ? err.message : '删除订阅失败', 'danger');
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <div
        className={`tower-card-compact p-4 transition-opacity ${
          subscription.isEnabled ? 'opacity-100' : 'opacity-65'
        }`}
      >
        <div className="flex items-start gap-3">
          <div className="pt-0.5">
            <SelectionIndicator
              selected={subscription.isEnabled}
              onToggle={() => void toggleEnabled()}
              label={subscription.isEnabled ? `停用 ${subscription.name}` : `启用 ${subscription.name}`}
            />
          </div>

          <div className="flex-1 min-w-0">
            <div className="flex items-center justify-between gap-2">
              <button
                type="button"
                onClick={() => setExpanded((v) => !v)}
                className="flex items-center gap-1.5 min-w-0 text-left group cursor-pointer"
              >
                <span className="font-semibold text-[15px] text-[var(--tower-text)] truncate group-hover:text-[var(--tower-accent)] transition-colors">
                  {subscription.name}
                </span>
                <span className="shrink-0 text-xs tabular-nums px-2 py-0.5 rounded-full bg-[var(--tower-surface-subtle)] text-[var(--tower-text-secondary)]">
                  {activeCount}/{totalCount}
                </span>
                {expanded ? (
                  <ChevronDown className="w-4 h-4 text-[var(--tower-text-tertiary)] shrink-0" />
                ) : (
                  <ChevronRight className="w-4 h-4 text-[var(--tower-text-tertiary)] shrink-0" />
                )}
              </button>

              <div className="flex items-center gap-1 shrink-0">
                <button
                  type="button"
                  onClick={() => void refreshSingleSubscription(subscription.id, subscription.name)}
                  disabled={isRefreshing}
                  aria-label="刷新订阅"
                  title="刷新订阅"
                  className="w-8 h-8 rounded-full flex items-center justify-center text-[var(--tower-accent)] hover:bg-[var(--tower-accent-soft)] transition-colors cursor-pointer"
                >
                  <RefreshCw className={`w-4 h-4 ${isRefreshing ? 'animate-spin' : ''}`} />
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setEditName(subscription.name);
                    setEditUrl(subscription.urlString);
                    setEditUA(subscription.requestOptions?.userAgent ?? '');
                    setEditDoH(subscription.requestOptions?.dnsOverHTTPSURL ?? '');
                    setEditOpen(true);
                  }}
                  aria-label="编辑订阅"
                  title="编辑订阅"
                  className="w-8 h-8 rounded-full flex items-center justify-center text-[var(--tower-text-secondary)] hover:text-[var(--tower-text)] hover:bg-[var(--tower-surface-subtle)] transition-colors cursor-pointer"
                >
                  <Edit3 className="w-4 h-4" />
                </button>
                <button
                  type="button"
                  onClick={() => setConfirmDelete(true)}
                  aria-label="删除订阅"
                  title="删除订阅"
                  className="w-8 h-8 rounded-full flex items-center justify-center text-[var(--tower-text-secondary)] hover:text-red-500 hover:bg-red-500/10 transition-colors cursor-pointer"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>

            <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-[var(--tower-text-secondary)]">
              <span className="truncate max-w-[260px]">{extractHost(subscription.urlString)}</span>
              {subscription.lastUpdatedAt && (
                <span className="inline-flex items-center gap-1">
                  <Clock className="w-3 h-3" />
                  <span>
                    {new Date(subscription.lastUpdatedAt).toLocaleString('zh-CN', {
                      month: 'numeric',
                      day: 'numeric',
                      hour: '2-digit',
                      minute: '2-digit',
                    })}
                  </span>
                </span>
              )}
              {expiry && (
                <span
                  className={`inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-medium ${
                    expiry.expired
                      ? 'bg-red-500/12 text-red-600 dark:text-red-400'
                      : expiry.soon
                        ? 'bg-amber-500/12 text-amber-600 dark:text-amber-400'
                        : 'bg-[var(--tower-surface-subtle)] text-[var(--tower-text-secondary)]'
                  }`}
                >
                  {expiry.label}
                </span>
              )}
            </div>

            {/* Traffic Usage Bar */}
            {hasTraffic && (
              <div className="mt-2.5 space-y-1">
                <div className="flex items-center justify-between text-[11px] tabular-nums text-[var(--tower-text-secondary)]">
                  <span>
                    已用 {formatBytes(usedBytes)} / 总计 {formatBytes(totalBytes)}
                  </span>
                  <span className="font-medium text-[var(--tower-text)]">
                    剩余 {formatBytes(remainingBytes)} ({100 - usedPercent}%)
                  </span>
                </div>
                <div className="h-1.5 w-full rounded-full bg-[var(--tower-surface-subtle)] overflow-hidden">
                  <div
                    className={`h-full rounded-full transition-all ${
                      usedPercent >= 90
                        ? 'bg-red-500'
                        : usedPercent >= 75
                          ? 'bg-amber-500'
                          : 'bg-[var(--tower-accent)]'
                    }`}
                    style={{ width: `${usedPercent}%` }}
                  />
                </div>
              </div>
            )}

            {/* Notices & Error */}
            {subscription.lastError && (
              <div className="mt-2 flex items-start gap-1.5 text-xs text-red-600 dark:text-red-400 bg-red-500/8 px-2.5 py-1.5 rounded-[10px]">
                <AlertCircle className="w-3.5 h-3.5 shrink-0 mt-0.5" />
                <span>{subscription.lastError}</span>
              </div>
            )}

            {usage?.notices && usage.notices.length > 0 && (
              <div className="mt-2 flex flex-wrap gap-1.5">
                {usage.notices.map((notice, idx) => (
                  <span
                    key={idx}
                    className="text-[11px] px-2 py-0.5 rounded-[8px] bg-[var(--tower-surface-subtle)] text-[var(--tower-text-secondary)]"
                  >
                    {notice}
                  </span>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Expandable node rows — no slide-from-top transition per CLAUDE.md #7 */}
        {expanded && (
          <div className="mt-3 pt-3 border-t border-[var(--tower-border)] space-y-1">
            {nodes.filter((n) => !n.isSubscriptionMetadata).length === 0 ? (
              <p className="text-xs text-[var(--tower-text-secondary)] py-1">
                该订阅暂无可用节点
              </p>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-1.5 max-h-64 overflow-y-auto pr-1">
                {nodes
                  .filter((n) => !n.isSubscriptionMetadata)
                  .map((n) => {
                    const rt = state?.runtime[n.id];
                    const isExcluded = excludedSet.has(n.id);
                    const flag = rt?.flagEmoji ?? '';
                    const ms = rt?.latency?.milliseconds;
                    return (
                      <div
                        key={n.id}
                        className={`flex items-center justify-between gap-2 px-2.5 py-1.5 rounded-[10px] bg-[var(--tower-surface-subtle)]/70 text-xs ${
                          isExcluded ? 'opacity-50 line-through' : ''
                        }`}
                      >
                        <span className="truncate text-[var(--tower-text)]">
                          {flag ? `${flag} ` : ''}
                          {n.name}
                        </span>
                        <span className="shrink-0 tabular-nums text-[11px] text-[var(--tower-text-secondary)]">
                          {ms != null ? `${ms}ms` : n.kind.toUpperCase()}
                        </span>
                      </div>
                    );
                  })}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Edit Modal */}
      <Modal
        open={editOpen}
        onClose={() => setEditOpen(false)}
        title="编辑订阅"
        subtitle={subscription.name}
        footer={
          <>
            <button
              type="button"
              onClick={() => setEditOpen(false)}
              className="px-4 py-2 rounded-[12px] text-sm font-medium text-[var(--tower-text-secondary)] hover:text-[var(--tower-text)] cursor-pointer"
            >
              取消
            </button>
            <button
              type="button"
              onClick={() => void handleSaveEdit()}
              disabled={saving}
              className="px-4 py-2 rounded-[12px] bg-[var(--tower-accent)] text-white text-sm font-semibold hover:opacity-92 disabled:opacity-50 cursor-pointer"
            >
              {saving ? '保存中…' : '保存'}
            </button>
          </>
        }
      >
        <div className="space-y-4">
          <div>
            <label className="block text-xs font-medium text-[var(--tower-text-secondary)] mb-1.5">
              订阅名称
            </label>
            <input
              type="text"
              value={editName}
              onChange={(e) => setEditName(e.target.value)}
              className="w-full px-3.5 py-2.5 text-sm tower-input"
              placeholder="例如：主力机场"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-[var(--tower-text-secondary)] mb-1.5">
              订阅链接
            </label>
            <input
              type="url"
              value={editUrl}
              onChange={(e) => setEditUrl(e.target.value)}
              className="w-full px-3.5 py-2.5 text-sm tower-input font-mono"
              placeholder="https://..."
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-[var(--tower-text-secondary)] mb-1.5">
              自定义 User-Agent（可选）
            </label>
            <input
              type="text"
              value={editUA}
              onChange={(e) => setEditUA(e.target.value)}
              className="w-full px-3.5 py-2.5 text-sm tower-input font-mono"
              placeholder="默认留空，使用塔台内置标识"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-[var(--tower-text-secondary)] mb-1.5">
              自定义 DoH 解析地址（可选）
            </label>
            <input
              type="url"
              value={editDoH}
              onChange={(e) => setEditDoH(e.target.value)}
              className="w-full px-3.5 py-2.5 text-sm tower-input font-mono"
              placeholder="https://dns.alidns.com/dns-query"
            />
          </div>
        </div>
      </Modal>

      {/* Delete Confirmation Modal */}
      <Modal
        open={confirmDelete}
        onClose={() => setConfirmDelete(false)}
        title="确认删除订阅"
        maxWidthClass="max-w-sm"
        footer={
          <>
            <button
              type="button"
              onClick={() => setConfirmDelete(false)}
              className="px-4 py-2 rounded-[12px] text-sm font-medium text-[var(--tower-text-secondary)] hover:text-[var(--tower-text)] cursor-pointer"
            >
              取消
            </button>
            <button
              type="button"
              onClick={() => void handleDelete()}
              disabled={saving}
              className="px-4 py-2 rounded-[12px] bg-red-500 text-white text-sm font-semibold hover:bg-red-600 disabled:opacity-50 cursor-pointer"
            >
              {saving ? '删除中…' : '确认删除'}
            </button>
          </>
        }
      >
        <p className="text-sm text-[var(--tower-text-secondary)] leading-relaxed">
          确定要删除订阅 <strong className="text-[var(--tower-text)]">「{subscription.name}」</strong> 吗？其所属节点也会一并移除。
        </p>
      </Modal>
    </>
  );
}
