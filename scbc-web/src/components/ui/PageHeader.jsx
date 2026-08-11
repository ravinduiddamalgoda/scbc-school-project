/**
 * Consistent page title block: eyebrow, heading, supporting line and the
 * page-level actions on the right.
 */
export default function PageHeader({ eyebrow, title, description, actions, icon }) {
  return (
    <header className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
      <div className="flex min-w-0 items-start gap-3">
        {icon && (
          <span
            className="mt-0.5 flex size-10 shrink-0 items-center justify-center rounded-panel bg-brand-50 text-brand-600 ring-1 ring-brand-500/15 dark:bg-brand-950 dark:text-brand-400"
            aria-hidden="true"
          >
            {icon}
          </span>
        )}

        <div className="min-w-0">
          {eyebrow && (
            <p className="text-xs font-semibold uppercase tracking-widest text-brand-600 dark:text-brand-400">
              {eyebrow}
            </p>
          )}
          <h1 className="mt-0.5 truncate text-xl font-bold tracking-tight text-slate-900 sm:text-2xl dark:text-slate-50">
            {title}
          </h1>
          {description && (
            <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{description}</p>
          )}
        </div>
      </div>

      {actions && <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div>}
    </header>
  );
}

/** Key/value row used inside the read-only detail drawers. */
export function DetailRow({ label, children, full = false }) {
  return (
    <div className={full ? 'sm:col-span-2' : ''}>
      <dt className="text-xs font-semibold uppercase tracking-wide text-slate-400 dark:text-slate-500">
        {label}
      </dt>
      <dd className="mt-1 break-words text-sm text-slate-800 dark:text-slate-200">{children}</dd>
    </div>
  );
}

/** Groups related fields inside a long form. */
export function FormSection({ title, description, children, columns = 2 }) {
  return (
    <section className="mb-6 last:mb-0">
      <div className="mb-3 border-b border-slate-200 pb-2 dark:border-slate-800">
        <h3 className="text-sm font-semibold text-slate-800 dark:text-slate-200">{title}</h3>
        {description && (
          <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">{description}</p>
        )}
      </div>

      <div className={`grid gap-4 ${columns === 2 ? 'sm:grid-cols-2' : 'grid-cols-1'}`}>
        {children}
      </div>
    </section>
  );
}
