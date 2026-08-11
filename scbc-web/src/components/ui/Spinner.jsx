export default function Spinner({ className = 'size-5', label }) {
  return (
    <>
      <svg
        className={`animate-spin ${className}`}
        viewBox="0 0 24 24"
        fill="none"
        aria-hidden="true"
      >
        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
        <path
          className="opacity-90"
          fill="currentColor"
          d="M4 12a8 8 0 0 1 8-8v4a4 4 0 0 0-4 4H4z"
        />
      </svg>
      {label && <span className="sr-only">{label}</span>}
    </>
  );
}

/** Full-panel loading state used while a page fetches its first payload. */
export function LoadingPanel({ label = 'Loading' }) {
  return (
    <div className="flex min-h-56 flex-col items-center justify-center gap-3 text-slate-400 dark:text-slate-500">
      <Spinner className="size-7" />
      <p className="text-sm font-medium">{label}…</p>
    </div>
  );
}
