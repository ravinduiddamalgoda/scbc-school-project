import { useId } from 'react';

/**
 * Shared chrome for every form control: label, required marker, error text and
 * the validation ring. Keeping it in one place is what makes the forms in this
 * app look identical without repeating class strings.
 */
function FieldShell({ label, required, error, hint, htmlFor, className = '', children }) {
  return (
    <div className={className}>
      {label && (
        <label
          htmlFor={htmlFor}
          className="mb-1.5 block text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400"
        >
          {label}
          {required && (
            <span className="ml-0.5 text-negative-500" aria-hidden="true">
              *
            </span>
          )}
        </label>
      )}

      {children}

      {error ? (
        <p className="mt-1.5 flex items-start gap-1 text-xs font-medium text-negative-600 dark:text-negative-500">
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.2"
            strokeLinecap="round"
            className="mt-px size-3.5 shrink-0"
            aria-hidden="true"
          >
            <path d="M12 8v5m0 3h.01" />
            <circle cx="12" cy="12" r="9" />
          </svg>
          {error}
        </p>
      ) : (
        hint && <p className="mt-1.5 text-xs text-slate-400 dark:text-slate-500">{hint}</p>
      )}
    </div>
  );
}

const CONTROL_BASE =
  'w-full rounded-lg border bg-white px-3 py-2 text-sm text-slate-800 shadow-sm transition placeholder:text-slate-400 ' +
  'focus:outline-none focus:ring-2 disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-500 ' +
  'dark:bg-slate-900 dark:text-slate-100 dark:placeholder:text-slate-500 dark:disabled:bg-slate-800';

const controlState = (error) =>
  error
    ? 'border-negative-500 focus:border-negative-500 focus:ring-negative-500/30 dark:border-negative-500'
    : 'border-slate-300 focus:border-brand-500 focus:ring-brand-500/30 dark:border-slate-700';

export function TextField({
  label,
  error,
  hint,
  required,
  className,
  type = 'text',
  ...props
}) {
  const id = useId();
  return (
    <FieldShell
      label={label}
      error={error}
      hint={hint}
      required={required}
      htmlFor={id}
      className={className}
    >
      <input
        id={id}
        type={type}
        aria-invalid={error ? true : undefined}
        className={`${CONTROL_BASE} ${controlState(error)}`}
        {...props}
      />
    </FieldShell>
  );
}

export function TextArea({ label, error, hint, required, className, rows = 3, ...props }) {
  const id = useId();
  return (
    <FieldShell
      label={label}
      error={error}
      hint={hint}
      required={required}
      htmlFor={id}
      className={className}
    >
      <textarea
        id={id}
        rows={rows}
        aria-invalid={error ? true : undefined}
        className={`${CONTROL_BASE} resize-y ${controlState(error)}`}
        {...props}
      />
    </FieldShell>
  );
}

/**
 * @param options - array of { value, label }
 */
export function SelectField({
  label,
  error,
  hint,
  required,
  className,
  options = [],
  placeholder = 'Select…',
  ...props
}) {
  const id = useId();
  return (
    <FieldShell
      label={label}
      error={error}
      hint={hint}
      required={required}
      htmlFor={id}
      className={className}
    >
      <div className="relative">
        <select
          id={id}
          aria-invalid={error ? true : undefined}
          className={`${CONTROL_BASE} appearance-none pr-9 ${controlState(error)}`}
          {...props}
        >
          <option value="">{placeholder}</option>
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>

        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-slate-400"
          aria-hidden="true"
        >
          <path d="m6 9 6 6 6-6" />
        </svg>
      </div>
    </FieldShell>
  );
}

/**
 * @param options - array of { value, label }
 */
export function RadioGroup({ label, error, required, className, name, value, onChange, options = [] }) {
  return (
    <FieldShell label={label} error={error} required={required} className={className}>
      <div className="flex flex-wrap gap-2" role="radiogroup" aria-label={label}>
        {options.map((option) => {
          const checked = String(value ?? '') === String(option.value);
          return (
            <label
              key={option.value}
              className={[
                'flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-sm font-medium transition',
                checked
                  ? 'border-brand-500 bg-brand-50 text-brand-800 ring-1 ring-brand-500 dark:bg-brand-950 dark:text-brand-200'
                  : 'border-slate-300 bg-white text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300 dark:hover:bg-slate-800',
              ].join(' ')}
            >
              <input
                type="radio"
                name={name}
                value={option.value}
                checked={checked}
                onChange={onChange}
                className="size-4 accent-brand-600"
              />
              {option.label}
            </label>
          );
        })}
      </div>
    </FieldShell>
  );
}

export function Checkbox({ label, description, checked, onChange, disabled, className = '', ...props }) {
  const id = useId();
  return (
    <label
      htmlFor={id}
      className={[
        'flex cursor-pointer items-start gap-3 rounded-lg border border-slate-200 bg-white p-3 transition',
        disabled
          ? 'cursor-not-allowed opacity-60'
          : 'hover:border-slate-300 hover:bg-slate-50 dark:hover:border-slate-600 dark:hover:bg-slate-800',
        'dark:border-slate-700 dark:bg-slate-900',
        className,
      ].join(' ')}
    >
      <input
        id={id}
        type="checkbox"
        checked={!!checked}
        onChange={onChange}
        disabled={disabled}
        className="mt-0.5 size-4 shrink-0 rounded accent-brand-600"
        {...props}
      />
      <span className="min-w-0">
        <span className="block text-sm font-medium text-slate-700 dark:text-slate-200">{label}</span>
        {description && (
          <span className="mt-0.5 block text-xs text-slate-400 dark:text-slate-500">{description}</span>
        )}
      </span>
    </label>
  );
}

/** A labelled on/off switch, used for account status. */
export function Toggle({ label, description, checked, onChange, disabled }) {
  const id = useId();
  return (
    <div className="flex items-center justify-between gap-4 rounded-lg border border-slate-200 bg-white p-3 dark:border-slate-700 dark:bg-slate-900">
      <span className="min-w-0">
        <label htmlFor={id} className="block text-sm font-medium text-slate-700 dark:text-slate-200">
          {label}
        </label>
        {description && (
          <span className="mt-0.5 block text-xs text-slate-400 dark:text-slate-500">{description}</span>
        )}
      </span>

      <button
        id={id}
        type="button"
        role="switch"
        aria-checked={!!checked}
        aria-label={label}
        disabled={disabled}
        onClick={() => onChange(!checked)}
        className={[
          'relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full transition disabled:cursor-not-allowed disabled:opacity-60',
          checked ? 'bg-positive-500' : 'bg-slate-300 dark:bg-slate-600',
        ].join(' ')}
      >
        <span
          className={[
            'pointer-events-none absolute top-0.5 size-5 rounded-full bg-white shadow transition-all',
            checked ? 'left-[1.375rem]' : 'left-0.5',
          ].join(' ')}
        />
      </button>
    </div>
  );
}

export { FieldShell };
