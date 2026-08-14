import { useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';

const WIDTHS = {
  md: 'sm:max-w-lg',
  lg: 'sm:max-w-2xl',
  xl: 'sm:max-w-4xl',
};

/**
 * Slide-over panel, the React equivalent of the Bootstrap offcanvas the old UI
 * used for its forms and detail views.
 *
 * Handles the accessibility work the original markup skipped: focus moves into
 * the panel on open, Escape closes it, and focus is trapped while it is open.
 */
export default function Drawer({
  open,
  onClose,
  title,
  description,
  size = 'lg',
  footer,
  children,
}) {
  const panelRef = useRef(null);
  const previouslyFocused = useRef(null);

  /**
   * Callers write `onClose={() => setOpen(false)}`, so the prop is a new
   * function on every render. Depending on it directly would re-run the effect
   * below on every keystroke in the panel - and its cleanup restores focus to
   * whatever opened the drawer, so the field being typed into lost focus after
   * a single character. Held in a ref instead: the handler stays current
   * without being a dependency.
   */
  const onCloseRef = useRef(onClose);
  useEffect(() => {
    onCloseRef.current = onClose;
  });

  useEffect(() => {
    if (!open) return undefined;

    previouslyFocused.current = document.activeElement;

    // Stop the page behind the drawer from scrolling.
    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    // Move focus to the first control inside the panel.
    const focusTimer = window.setTimeout(() => {
      const firstFocusable = panelRef.current?.querySelector(
        'input:not([type="hidden"]), select, textarea, button, [href], [tabindex]:not([tabindex="-1"])',
      );
      (firstFocusable ?? panelRef.current)?.focus();
    }, 60);

    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        event.stopPropagation();
        onCloseRef.current();
        return;
      }

      if (event.key !== 'Tab' || !panelRef.current) return;

      const focusable = Array.from(
        panelRef.current.querySelectorAll(
          'input:not([type="hidden"]):not([disabled]), select:not([disabled]), textarea:not([disabled]), button:not([disabled]), [href], [tabindex]:not([tabindex="-1"])',
        ),
      ).filter((element) => element.offsetParent !== null);

      if (focusable.length === 0) return;

      const first = focusable[0];
      const last = focusable[focusable.length - 1];

      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener('keydown', handleKeyDown);

    return () => {
      window.clearTimeout(focusTimer);
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = originalOverflow;
      previouslyFocused.current?.focus?.();
    };
    // Deliberately only `open`: see onCloseRef above.
  }, [open]);

  if (!open) return null;

  return createPortal(
    <div className="fixed inset-0 z-50 flex justify-end" role="dialog" aria-modal="true" aria-label={title}>
      <div
        className="animate-fade-in absolute inset-0 bg-slate-900/50 backdrop-blur-[2px]"
        onClick={onClose}
        aria-hidden="true"
      />

      <div
        ref={panelRef}
        tabIndex={-1}
        className={[
          'animate-slide-in relative flex h-full w-full flex-col bg-slate-50 shadow-raised outline-none',
          'dark:bg-slate-900',
          WIDTHS[size] ?? WIDTHS.lg,
        ].join(' ')}
      >
        <header className="flex items-start gap-4 border-b border-slate-200 bg-white px-5 py-4 dark:border-slate-800 dark:bg-slate-900">
          <div className="min-w-0 flex-1">
            <h2 className="truncate text-base font-semibold text-slate-900 dark:text-slate-100">
              {title}
            </h2>
            {description && (
              <p className="mt-0.5 text-sm text-slate-500 dark:text-slate-400">{description}</p>
            )}
          </div>

          <button
            type="button"
            onClick={onClose}
            className="-mr-1 rounded-lg p-2 text-slate-400 transition hover:bg-slate-100 hover:text-slate-600 dark:hover:bg-slate-800 dark:hover:text-slate-300"
            aria-label="Close panel"
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              className="size-5"
            >
              <path d="M18 6 6 18M6 6l12 12" />
            </svg>
          </button>
        </header>

        <div className="min-h-0 flex-1 overflow-y-auto px-5 py-5">{children}</div>

        {footer && (
          <footer className="flex items-center justify-end gap-3 border-t border-slate-200 bg-white px-5 py-4 dark:border-slate-800 dark:bg-slate-900">
            {footer}
          </footer>
        )}
      </div>
    </div>,
    document.body,
  );
}
