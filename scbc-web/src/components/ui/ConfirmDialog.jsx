import { useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import Button from './Button';

/**
 * Blocking confirmation for destructive actions, replacing the SweetAlert2
 * confirm the old UI used before a delete.
 */
export default function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel = 'Delete',
  cancelLabel = 'Cancel',
  variant = 'danger',
  loading = false,
  onConfirm,
  onCancel,
}) {
  const confirmRef = useRef(null);

  useEffect(() => {
    if (!open) return undefined;

    const timer = window.setTimeout(() => confirmRef.current?.focus(), 60);
    const handleKeyDown = (event) => {
      if (event.key === 'Escape' && !loading) onCancel();
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => {
      window.clearTimeout(timer);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [open, onCancel, loading]);

  if (!open) return null;

  return createPortal(
    <div
      className="fixed inset-0 z-60 flex items-center justify-center p-4"
      role="alertdialog"
      aria-modal="true"
      aria-labelledby="confirm-title"
    >
      <div
        className="animate-fade-in absolute inset-0 bg-slate-900/55 backdrop-blur-[2px]"
        onClick={() => !loading && onCancel()}
        aria-hidden="true"
      />

      <div className="animate-rise relative w-full max-w-md rounded-panel bg-white p-5 shadow-raised dark:bg-slate-900">
        <div className="flex gap-4">
          <span
            className={[
              'flex size-10 shrink-0 items-center justify-center rounded-full',
              variant === 'danger'
                ? 'bg-negative-50 text-negative-600 dark:bg-negative-900/40 dark:text-negative-500'
                : 'bg-notice-50 text-notice-600 dark:bg-notice-900/40 dark:text-notice-500',
            ].join(' ')}
            aria-hidden="true"
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              className="size-5"
            >
              <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0Z" />
              <path d="M12 9v4m0 4h.01" />
            </svg>
          </span>

          <div className="min-w-0 flex-1">
            <h2
              id="confirm-title"
              className="text-base font-semibold text-slate-900 dark:text-slate-100"
            >
              {title}
            </h2>
            <p className="mt-1.5 text-sm leading-relaxed text-slate-600 dark:text-slate-400">
              {message}
            </p>
          </div>
        </div>

        <div className="mt-6 flex justify-end gap-3">
          <Button variant="secondary" onClick={onCancel} disabled={loading}>
            {cancelLabel}
          </Button>
          <Button ref={confirmRef} variant={variant} onClick={onConfirm} loading={loading}>
            {confirmLabel}
          </Button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
