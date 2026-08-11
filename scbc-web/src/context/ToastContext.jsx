import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react';

const ToastContext = createContext(null);

const VARIANTS = {
  success: {
    bar: 'bg-positive-500',
    icon: 'M20 6 9 17l-5-5',
    iconWrap: 'bg-positive-50 text-positive-600 dark:bg-positive-900/40 dark:text-positive-500',
  },
  error: {
    bar: 'bg-negative-500',
    icon: 'M12 9v4m0 4h.01M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0Z',
    iconWrap: 'bg-negative-50 text-negative-600 dark:bg-negative-900/40 dark:text-negative-500',
  },
  info: {
    bar: 'bg-brand-500',
    icon: 'M12 16v-4m0-4h.01M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z',
    iconWrap: 'bg-brand-50 text-brand-600 dark:bg-brand-950 dark:text-brand-400',
  },
};

/**
 * Replaces the SweetAlert2 popups of the previous UI with non-blocking
 * notifications, so a save confirmation never interrupts the next action.
 */
export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const nextId = useRef(1);

  const dismiss = useCallback((id) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const push = useCallback(
    (message, variant = 'info', timeout = 5000) => {
      const id = nextId.current++;
      setToasts((current) => [...current, { id, message, variant }]);
      if (timeout > 0) {
        setTimeout(() => dismiss(id), timeout);
      }
      return id;
    },
    [dismiss],
  );

  const value = useMemo(
    () => ({
      push,
      dismiss,
      success: (message) => push(message, 'success'),
      // Errors linger longer: they usually need to be read, not glanced at.
      error: (message) => push(message, 'error', 8000),
      info: (message) => push(message, 'info'),
    }),
    [push, dismiss],
  );

  return (
    <ToastContext.Provider value={value}>
      {children}

      <div
        className="pointer-events-none fixed inset-x-0 bottom-0 z-100 flex flex-col items-center gap-2 p-4 sm:inset-x-auto sm:right-0 sm:top-0 sm:items-end"
        role="region"
        aria-live="polite"
        aria-label="Notifications"
      >
        {toasts.map((toast) => {
          const variant = VARIANTS[toast.variant] ?? VARIANTS.info;
          return (
            <div
              key={toast.id}
              className="animate-rise pointer-events-auto flex w-full max-w-sm items-start gap-3 overflow-hidden rounded-panel bg-white shadow-raised ring-1 ring-slate-900/5 dark:bg-slate-900 dark:ring-white/10"
            >
              <span className={`w-1 self-stretch ${variant.bar}`} aria-hidden="true" />

              <span
                className={`mt-3 flex size-7 shrink-0 items-center justify-center rounded-full ${variant.iconWrap}`}
                aria-hidden="true"
              >
                <svg
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2.2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  className="size-4"
                >
                  <path d={variant.icon} />
                </svg>
              </span>

              <p className="flex-1 py-3 pr-1 text-sm leading-snug text-slate-700 dark:text-slate-200">
                {toast.message}
              </p>

              <button
                type="button"
                onClick={() => dismiss(toast.id)}
                className="mt-2 mr-2 rounded-md p-1.5 text-slate-400 transition hover:bg-slate-100 hover:text-slate-600 dark:hover:bg-slate-800 dark:hover:text-slate-300"
                aria-label="Dismiss notification"
              >
                <svg
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  className="size-4"
                >
                  <path d="M18 6 6 18M6 6l12 12" />
                </svg>
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used inside a ToastProvider.');
  }
  return context;
}
