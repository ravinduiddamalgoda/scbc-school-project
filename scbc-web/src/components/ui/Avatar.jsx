import { initials } from '@/lib/format';

const SIZES = {
  sm: 'size-8 text-xs',
  md: 'size-10 text-sm',
  lg: 'size-16 text-lg',
  xl: 'size-24 text-2xl',
};

/**
 * Photo with an initials fallback.
 *
 * `src` is the data URL the API returns directly - the old client had to run
 * atob() on a double-encoded value before it could be displayed.
 */
export default function Avatar({ src, name, size = 'md', className = '' }) {
  const dimension = SIZES[size] ?? SIZES.md;

  if (src) {
    return (
      <img
        src={src}
        alt={name ? `${name}'s photo` : 'Profile photo'}
        className={`${dimension} shrink-0 rounded-full object-cover ring-1 ring-slate-900/10 dark:ring-white/15 ${className}`}
      />
    );
  }

  return (
    <span
      className={`${dimension} flex shrink-0 items-center justify-center rounded-full bg-brand-100 font-semibold text-brand-700 ring-1 ring-brand-500/20 dark:bg-brand-950 dark:text-brand-300 ${className}`}
      aria-label={name ? `${name} (no photo)` : 'No photo'}
    >
      {initials(name)}
    </span>
  );
}
