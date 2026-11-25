import React, { useEffect } from 'react';
import { X } from 'lucide-react';

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  maxWidthClass?: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
}

export function Modal({
  open,
  onClose,
  title,
  subtitle,
  maxWidthClass = 'max-w-lg',
  children,
  footer,
}: ModalProps) {
  useEffect(() => {
    if (!open) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4"
      role="dialog"
      aria-modal="true"
      aria-label={title}
    >
      <div
        className="fixed inset-0 bg-black/35 backdrop-blur-sm transition-opacity"
        onClick={onClose}
      />
      <div
        className={`relative z-10 w-full ${maxWidthClass} max-h-[90vh] flex flex-col rounded-t-[26px] sm:rounded-[26px] border border-[var(--tower-border-strong)] bg-[var(--tower-surface)] shadow-[var(--tower-shadow-float)] animate-tower-surface overflow-hidden`}
      >
        <div className="flex items-start justify-between gap-3 px-6 pt-5 pb-3.5 border-b border-[var(--tower-border)]">
          <div className="min-w-0">
            <h2 className="text-lg font-semibold tracking-tight text-[var(--tower-text)] truncate">
              {title}
            </h2>
            {subtitle && (
              <p className="mt-0.5 text-xs text-[var(--tower-text-secondary)]">{subtitle}</p>
            )}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="关闭"
            className="shrink-0 w-8 h-8 rounded-full flex items-center justify-center bg-[var(--tower-surface-subtle)] text-[var(--tower-text-secondary)] hover:text-[var(--tower-text)] transition-colors cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-6 py-4">{children}</div>

        {footer && (
          <div className="px-6 py-3.5 border-t border-[var(--tower-border)] bg-[var(--tower-surface-subtle)]/55 flex items-center justify-end gap-2.5">
            {footer}
          </div>
        )}
      </div>
    </div>
  );
}
