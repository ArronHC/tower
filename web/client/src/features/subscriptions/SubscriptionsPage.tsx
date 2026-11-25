import { useState } from 'react';
import { AddSourceModal } from '@/features/subscriptions/AddSourceModal';
import { MetricPills } from '@/features/subscriptions/MetricPills';
import { NodeSection } from '@/features/subscriptions/NodeSection';
import { SubscriptionList } from '@/features/subscriptions/SubscriptionList';
import { WorldDotMapCanvas } from '@/features/subscriptions/WorldDotMapCanvas';

export function SubscriptionsPage() {
  const [addOpen, setAddOpen] = useState(false);

  return (
    <div className="space-y-6">
      <MetricPills onOpenAdd={() => setAddOpen(true)} />
      <WorldDotMapCanvas />
      <SubscriptionList onOpenAdd={() => setAddOpen(true)} />
      <NodeSection />
      <AddSourceModal open={addOpen} onClose={() => setAddOpen(false)} />
    </div>
  );
}
