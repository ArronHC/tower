import React from 'react';

interface MetricPillProps {
  label: string;
  value: number;
  total?: number;
  icon: React.ReactNode;
  tintClass?: string;
}

export function MetricPill({
  label,
  value,
  total,
  icon,
  tintClass = 'text-[var(--tower-accent)]',
}: MetricPillProps) {
  return (
    <div className="flex flex-col justify-between px-4 py-3 rounded-[16px] bg-[var(--tower-surface)] border border-[var(--tower-border)] shadow-[var(--tower-shadow)]">
      <div className="flex items-center gap-1.5 text-xs font-medium text-[var(--tower-text-secondary)]">
        <span className={tintClass}>{icon}</span>
        <span className="truncate">{label}</span>
      </div>
      <div className="mt-1.5 flex items-baseline gap-0.5 tabular-nums">
        <span className="text-2xl font-bold tracking-tight text-[var(--tower-text)]">
          {value.toLocaleString()}
        </span>
        {total !== undefined && (
          <span className="text-sm font-semibold text-[var(--tower-text-secondary)]">
            /{total.toLocaleString()}
          </span>
        )}
      </div>
    </div>
  );
}
