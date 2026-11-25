import { useState } from 'react';

interface ClientIconProps {
  iconFile?: string;
  name: string;
  brandColorHex?: string;
  className?: string;
}

export function ClientIcon({
  iconFile,
  name,
  brandColorHex = '007AFF',
  className = 'w-9 h-9',
}: ClientIconProps) {
  const [failed, setFailed] = useState(false);

  if (!iconFile || failed) {
    return (
      <div
        className={`${className} rounded-[10px] flex items-center justify-center text-white font-semibold text-sm shrink-0 shadow-xs`}
        style={{ backgroundColor: `#${brandColorHex}` }}
      >
        {name.slice(0, 1).toUpperCase()}
      </div>
    );
  }

  return (
    <img
      src={`/assets/clients/${iconFile}`}
      alt={name}
      onError={() => setFailed(true)}
      className={`${className} rounded-[10px] object-cover shrink-0 shadow-xs bg-[var(--tower-surface-subtle)]`}
      draggable={false}
    />
  );
}
