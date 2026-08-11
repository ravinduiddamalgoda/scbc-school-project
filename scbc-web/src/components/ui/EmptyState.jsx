export default function EmptyState({ title, message, action, icon }) {
  return (
    <div className="flex min-h-56 flex-col items-center justify-center gap-3 px-6 py-12 text-center">
      <span
        className="flex size-12 items-center justify-center rounded-full bg-slate-100 text-slate-400 dark:bg-slate-800 dark:text-slate-500"
        aria-hidden="true"
      >
        {icon ?? (
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.7"
            strokeLinecap="round"
            strokeLinejoin="round"
            className="size-6"
          >
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z" />
            <path d="M14 2v6h6M9 15h6" />
          </svg>
        )}
      </span>

      <div>
        <p className="text-sm font-semibold text-slate-700 dark:text-slate-200">{title}</p>
        {message && (
          <p className="mt-1 max-w-sm text-sm text-slate-500 dark:text-slate-400">{message}</p>
        )}
      </div>

      {action}
    </div>
  );
}
