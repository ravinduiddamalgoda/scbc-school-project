/**
 * The card every Academic setup section sits in.
 *
 * Lifted out of AcademicSetupPage so panels that live in their own files —
 * the curriculum and the fee table — can use the same shell. Duplicating the
 * markup instead would be the usual way two sections of one screen quietly
 * drift apart.
 */
export default function SetupPanel({ title, description, actions, children }) {
  return (
    <section className="overflow-hidden rounded-panel bg-white shadow-panel ring-1 ring-slate-900/5 dark:bg-slate-900 dark:ring-white/10">
      <header className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-200 px-4 py-3 dark:border-slate-800">
        <div className="min-w-0">
          <h2 className="text-sm font-semibold text-slate-800 dark:text-slate-200">{title}</h2>
          <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">{description}</p>
        </div>
        {actions}
      </header>
      {children}
    </section>
  );
}
