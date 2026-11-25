import { Activity, Globe2, Plus, RefreshCw, Rss, Server } from 'lucide-react';
import { MetricPill } from '@/components/MetricPill';
import { useTower } from '@/state/TowerContext';

interface MetricPillsProps {
  onOpenAdd: () => void;
}

export function MetricPills({ onOpenAdd }: MetricPillsProps) {
  const {
    state,
    refreshingAll,
    testingLatency,
    refreshAllSubscriptions,
    testLatency,
  } = useTower();

  const stats = state?.stats ?? {
    subscriptionCount: 0,
    enabledSubscriptionCount: 0,
    totalNodeCount: 0,
    activeNodeCount: 0,
    countryCount: 0,
  };

  return (
    <div className="space-y-3">
      <div className="grid grid-cols-3 gap-3">
        <MetricPill
          label="订阅源"
          value={stats.enabledSubscriptionCount}
          total={stats.subscriptionCount}
          icon={<Rss className="w-3.5 h-3.5" />}
          tintClass="text-[var(--tower-accent)]"
        />
        <MetricPill
          label="活跃节点"
          value={stats.activeNodeCount}
          total={stats.totalNodeCount}
          icon={<Server className="w-3.5 h-3.5" />}
          tintClass="text-emerald-500"
        />
        <MetricPill
          label="覆盖国家"
          value={stats.countryCount}
          icon={<Globe2 className="w-3.5 h-3.5" />}
          tintClass="text-indigo-500"
        />
      </div>

      <div className="flex flex-wrap items-center gap-2.5">
        <button
          type="button"
          onClick={onOpenAdd}
          className="inline-flex items-center gap-1.5 px-4 py-2 rounded-[13px] bg-[var(--tower-accent)] text-white text-sm font-semibold hover:opacity-92 active:scale-[0.98] transition-all cursor-pointer shadow-xs"
        >
          <Plus className="w-4 h-4 stroke-[2.4]" />
          <span>添加订阅 / 节点</span>
        </button>

        <button
          type="button"
          onClick={() => void refreshAllSubscriptions()}
          disabled={refreshingAll || stats.enabledSubscriptionCount === 0}
          className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-[13px] bg-[var(--tower-surface)] border border-[var(--tower-border)] text-[var(--tower-text)] text-sm font-medium hover:bg-[var(--tower-surface-subtle)] disabled:opacity-45 transition-colors cursor-pointer"
        >
          <RefreshCw className={`w-4 h-4 text-[var(--tower-accent)] ${refreshingAll ? 'animate-spin' : ''}`} />
          <span>{refreshingAll ? '刷新中…' : '全部刷新'}</span>
        </button>

        <button
          type="button"
          onClick={() => void testLatency()}
          disabled={testingLatency || stats.activeNodeCount === 0}
          className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-[13px] bg-[var(--tower-surface)] border border-[var(--tower-border)] text-[var(--tower-text)] text-sm font-medium hover:bg-[var(--tower-surface-subtle)] disabled:opacity-45 transition-colors cursor-pointer"
        >
          <Activity className={`w-4 h-4 text-emerald-500 ${testingLatency ? 'animate-pulse' : ''}`} />
          <span>{testingLatency ? '测速中…' : '全部测速'}</span>
        </button>
      </div>
    </div>
  );
}
