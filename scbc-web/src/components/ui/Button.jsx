import { forwardRef } from 'react';
import Spinner from './Spinner';

const VARIANTS = {
  primary:
    'bg-brand-600 text-white shadow-sm hover:bg-brand-700 active:bg-brand-800 disabled:bg-brand-300 dark:disabled:bg-brand-900',
  secondary:
    'bg-white text-slate-700 ring-1 ring-inset ring-slate-300 hover:bg-slate-50 active:bg-slate-100 dark:bg-slate-800 dark:text-slate-200 dark:ring-slate-700 dark:hover:bg-slate-700',
  danger:
    'bg-negative-600 text-white shadow-sm hover:bg-negative-500 active:bg-negative-600 disabled:bg-negative-500/50',
  ghost:
    'text-slate-600 hover:bg-slate-100 active:bg-slate-200 dark:text-slate-300 dark:hover:bg-slate-800',
  subtle:
    'bg-brand-50 text-brand-700 hover:bg-brand-100 dark:bg-brand-950 dark:text-brand-300 dark:hover:bg-brand-900',
};

const SIZES = {
  sm: 'h-8 gap-1.5 px-3 text-xs',
  md: 'h-10 gap-2 px-4 text-sm',
  lg: 'h-11 gap-2 px-5 text-sm',
  icon: 'size-9 justify-center',
};

/**
 * The single button primitive. Every action in the app renders through it, so
 * sizing, focus and disabled states stay consistent.
 */
const Button = forwardRef(function Button(
  {
    variant = 'primary',
    size = 'md',
    loading = false,
    disabled = false,
    className = '',
    children,
    type = 'button',
    ...props
  },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={[
        'inline-flex shrink-0 items-center justify-center rounded-lg font-semibold transition',
        'disabled:cursor-not-allowed disabled:opacity-70',
        VARIANTS[variant] ?? VARIANTS.primary,
        SIZES[size] ?? SIZES.md,
        className,
      ].join(' ')}
      {...props}
    >
      {loading && <Spinner className="size-4" />}
      {children}
    </button>
  );
});

export default Button;
