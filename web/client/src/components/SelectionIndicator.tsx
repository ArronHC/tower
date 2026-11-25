import { Check } from 'lucide-react';

interface SelectionIndicatorProps {
  selected: boolean;
  onToggle?: () => void;
  label?: string;
  size?: 'sm' | 'md';
  disabled?: boolean;
}

export function SelectionIndicator({
  selected,
  onToggle,
  label,
  size = 'md',
  disabled = false,
}: SelectionIndicatorProps) {
  const dim = size === 'sm' ? 'w-5 h-5' : 'w-[24px] h-[24px]';
  const iconDim = size === 'sm' ? 'w-3 h-3' : 'w-3.5 h-3.5';

  const circle = (
    <span
      className={`${dim} rounded-full inline-flex items-center justify-center transition-all duration-150 shrink-0 ${
        selected
          ? 'bg-[var(--tower-accent)] border border-[var(--tower-accent)] text-white scale-100'
          : 'bg-transparent border-[1.5px] border-[var(--tower-text-tertiary)]/55 text-transparent scale-95'
      }`}
    >
      <Check className={`${iconDim} stroke-[3] transition-opacity ${selected ? 'opacity-100' : 'opacity-0'}`} />
    </span>
  );

  if (!onToggle) return circle;

  return (
    <button
      type="button"
      role="switch"
      aria-checked={selected}
      aria-label={label}
      disabled={disabled}
      onClick={(e) => {
        e.stopPropagation();
        onToggle();
      }}
      className="p-1.5 -m-1.5 rounded-full inline-flex items-center justify-center hover:opacity-80 active:opacity-65 disabled:opacity-40 transition-opacity cursor-pointer"
    >
      {circle}
    </button>
  );
}
