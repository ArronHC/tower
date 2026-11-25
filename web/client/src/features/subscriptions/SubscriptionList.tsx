import { Rss } from 'lucide-react';
import { SubscriptionCard } from '@/features/subscriptions/SubscriptionCard';
import { useTower } from '@/state/TowerContext';
import type { ProxyNode } from '@shared/index';

export function SubscriptionList({ onOpenAdd }: { onOpenAdd: () => void }) {
  const { state } = useTower();
  const subscriptions = state?.snapshot.subscriptions ?? [];
  const allNodes = state?.snapshot.nodes ?? [];

  const nodesBySub = new Map<string, ProxyNode[]>();
  for (const node of allNodes) {
    if (!node.sourceID) continue;
    const list = nodesBySub.get(node.sourceID) ?? [];
    list.push(node);
    nodesBySub.set(node.sourceID, list);
  }

  return (
    <section className="space-y-3">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold tracking-tight text-[var(--tower-text)]">
          订阅源
        </h2>
        <span className="text-xs font-medium text-[var(--tower-text-secondary)] tabular-nums">
          {subscriptions.length} 个订阅
        </span>
      </div>

      {subscriptions.length === 0 ? (
        <div className="tower-card p-8 text-center space-y-3">
          <div className="w-11 h-11 mx-auto rounded-full bg-[var(--tower-accent-soft)] text-[var(--tower-accent)] flex items-center justify-center">
            <Rss className="w-5 h-5" />
          </div>
          <div>
            <p className="text-sm font-semibold text-[var(--tower-text)]">尚未添加订阅</p>
            <p className="mt-1 text-xs text-[var(--tower-text-secondary)]">
              支持标准订阅 URL、Clash YAML、Surge 配置与单节点 URI 批量导入。
            </p>
          </div>
          <button
            type="button"
            onClick={onOpenAdd}
            className="inline-flex items-center px-4 py-2 rounded-[12px] bg-[var(--tower-accent)] text-white text-xs font-semibold hover:opacity-92 cursor-pointer"
          >
            添加第一个来源
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
          {subscriptions.map((sub) => (
            <SubscriptionCard
              key={sub.id}
              subscription={sub}
              nodes={nodesBySub.get(sub.id) ?? []}
            />
          ))}
        </div>
      )}
    </section>
  );
}
