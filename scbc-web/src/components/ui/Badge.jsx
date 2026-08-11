const TONES = {
  neutral: 'bg-slate-100 text-slate-600 ring-slate-500/20 dark:bg-slate-800 dark:text-slate-300',
  positive:
    'bg-positive-50 text-positive-600 ring-positive-500/25 dark:bg-positive-900/30 dark:text-positive-500',
  negative:
    'bg-negative-50 text-negative-600 ring-negative-500/25 dark:bg-negative-900/30 dark:text-negative-500',
  notice: 'bg-notice-50 text-notice-600 ring-notice-500/25 dark:bg-notice-900/30 dark:text-notice-500',
  brand: 'bg-brand-50 text-brand-700 ring-brand-500/25 dark:bg-brand-950 dark:text-brand-300',
};

export default function Badge({ tone = 'neutral', children, className = '' }) {
  return (
    <span
      className={[
        'inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-semibold ring-1 ring-inset',
        TONES[tone] ?? TONES.neutral,
        className,
      ].join(' ')}
    >
      {children}
    </span>
  );
}

/**
 * The tick/cross used throughout the privilege matrix. Colour alone never
 * carries the meaning - the glyph and the screen-reader label do.
 */
export function BoolMark({ value, label }) {
  return (
    <span
      className={[
        'inline-flex size-6 items-center justify-center rounded-full',
        value
          ? 'bg-positive-50 text-positive-600 dark:bg-positive-900/40 dark:text-positive-500'
          : 'bg-slate-100 text-slate-400 dark:bg-slate-800 dark:text-slate-600',
      ].join(' ')}
      title={label ? `${label}: ${value ? 'allowed' : 'denied'}` : undefined}
    >
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="3"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="size-3.5"
        aria-hidden="true"
      >
        {value ? <path d="M20 6 9 17l-5-5" /> : <path d="M18 6 6 18M6 6l12 12" />}
      </svg>
      <span className="sr-only">{value ? 'Allowed' : 'Denied'}</span>
    </span>
  );
}
