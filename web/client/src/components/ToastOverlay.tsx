import { AlertTriangle, Check, Info, Loader2, X, XCircle } from 'lucide-react';
import { useTower, type ToastTone } from '@/state/TowerContext';

const toneStyles: Record<
  ToastTone,
  { border: string; bg: string; badgeBg: string; shadow: string }
> = {
  neutral: {
    border: 'border-sky-500/25',
    bg: 'bg-[var(--tower-surface)]/95',
    badgeBg: 'bg-[var(--tower-accent)]',
    shadow: 'shadow-[0_12px_28px_-6px_rgba(10,132,255,0.18)]',
  },
  success: {
    border: 'border-emerald-500/45',
    bg: 'bg-[var(--tower-surface)]/95',
    badgeBg: 'bg-emerald-500',
    shadow: 'shadow-[0_12px_28px_-6px_rgba(22,163,74,0.22)]',
  },
  warning: {
    border: 'border-amber-500/45',
    bg: 'bg-[var(--tower-surface)]/95',
    badgeBg: 'bg-amber-500',
    shadow: 'shadow-[0_12px_28px_-6px_rgba(217,119,6,0.22)]',
  },
  danger: {
    border: 'border-red-500/45',
    bg: 'bg-[var(--tower-surface)]/95',
    badgeBg: 'bg-red-500',
    shadow: 'shadow-[0_12px_28px_-6px_rgba(220,38,38,0.22)]',
  },
};

export function ToastOverlay() {
  const { toasts, dismissToast } = useTower();
  if (toasts.length === 0) return null;

  return (
    <div
      className="fixed top-4 left-1/2 -translate-x-1/2 z-[60] flex flex-col items-center gap-2 pointer-events-none px-4 w-full max-w-[540px]"
      aria-live="polite"
    >
      {toasts.map((toast) => {
        const s = toneStyles[toast.tone];
        return (
          <div
            key={toast.id}
            className={`pointer-events-auto flex items-center gap-3 px-3.5 py-2.5 rounded-[17px] border backdrop-blur-xl ${s.border} ${s.bg} ${s.shadow} animate-tower-surface max-w-full`}
          >
            <div
              className={`w-7 h-7 rounded-full flex items-center justify-center text-white shrink-0 ${s.badgeBg}`}
            >
              {toast.spinning ? (
                <Loader2 className="w-3.5 h-3.5 animate-spin" />
              ) : toast.tone === 'success' ? (
                <Check className="w-3.5 h-3.5 stroke-[2.6]" />
              ) : toast.tone === 'warning' ? (
                <AlertTriangle className="w-3.5 h-3.5 stroke-[2.4]" />
              ) : toast.tone === 'danger' ? (
                <XCircle className="w-3.5 h-3.5 stroke-[2.4]" />
              ) : (
                <Info className="w-3.5 h-3.5 stroke-[2.4]" />
              )}
            </div>
            <span className="text-sm font-medium text-[var(--tower-text)] leading-snug">
              {toast.text}
            </span>
            {!toast.spinning && (
              <button
                type="button"
                onClick={() => dismissToast(toast.id)}
                aria-label="关闭提示"
                className="ml-1 p-1 rounded-full text-[var(--tower-text-tertiary)] hover:text-[var(--tower-text)] transition-colors cursor-pointer"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            )}
          </div>
        );
      })}
    </div>
  );
}
