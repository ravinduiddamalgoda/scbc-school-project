/**
 * Year selector shared by the class register and the reports page.
 *
 * The empty value is deliberate and is sent as "no year": the server then picks
 * the year flagged current. Defaulting in the client instead would mean two
 * places deciding what "this year" means, and they would eventually disagree.
 */
export default function AcademicYearPicker({ years, value, onChange, loading = false, id = 'academic-year' }) {
  const current = years.find((year) => year.current_year);

  return (
    <label htmlFor={id} className="flex items-center gap-2">
      <span className="text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
        Year
      </span>

      <select
        id={id}
        value={value}
        disabled={loading || years.length === 0}
        onChange={(event) => onChange(event.target.value)}
        className="h-10 rounded-lg border border-slate-300 bg-white px-3 text-sm text-slate-800 shadow-sm transition focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30 disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-400 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:disabled:bg-slate-800"
      >
        <option value="">
          {years.length === 0
            ? 'No academic years yet'
            : `Current${current ? ` (${current.name})` : ''}`}
        </option>
        {years.map((year) => (
          <option key={year.id} value={year.id}>
            {year.name}
            {year.current_year ? ' · current' : ''}
          </option>
        ))}
      </select>
    </label>
  );
}
