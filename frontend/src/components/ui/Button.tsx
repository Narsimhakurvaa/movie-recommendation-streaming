import { forwardRef } from 'react';
import type { ButtonHTMLAttributes } from 'react';
import { cva, type VariantProps } from 'class-variance-authority';
import { Loader2 } from 'lucide-react';
import { cn } from '@/lib/utils';

/**
 * The single button primitive.
 *
 * Every interactive control routes through here so focus rings, disabled
 * states and the loading affordance are consistent and cannot be forgotten.
 */
const buttonVariants = cva(
  [
    'inline-flex items-center justify-center gap-2 rounded-lg font-medium',
    'transition-colors duration-150 select-none whitespace-nowrap',
    // Disabled controls must not look clickable.
    'disabled:opacity-50 disabled:pointer-events-none',
    'focus-visible:outline-2 focus-visible:outline-offset-2',
  ].join(' '),
  {
    variants: {
      variant: {
        primary:
          'bg-[var(--accent)] text-[var(--accent-contrast)] hover:bg-[var(--accent-hover)] shadow-sm',
        secondary:
          'bg-[var(--surface-overlay)] text-[var(--text-primary)] border border-[var(--border-subtle)] hover:border-[var(--border-strong)]',
        ghost: 'text-[var(--text-secondary)] hover:bg-[var(--surface-sunken)] hover:text-[var(--text-primary)]',
        danger: 'bg-red-600 text-white hover:bg-red-700 shadow-sm',
        outline:
          'border border-[var(--border-strong)] text-[var(--text-primary)] hover:bg-[var(--surface-sunken)]',
      },
      size: {
        sm: 'h-8 px-3 text-sm',
        md: 'h-10 px-4 text-sm',
        lg: 'h-12 px-6 text-base',
        icon: 'h-10 w-10',
      },
    },
    defaultVariants: { variant: 'primary', size: 'md' },
  },
);

export interface ButtonProps
  extends ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  isLoading?: boolean;
  /** Announced to screen readers while `isLoading` is true. */
  loadingLabel?: string;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { className, variant, size, isLoading, loadingLabel = 'Loading', children, disabled, ...props },
  ref,
) {
  return (
    <button
      ref={ref}
      className={cn(buttonVariants({ variant, size }), className)}
      // A loading button must not be clickable twice.
      disabled={disabled || isLoading}
      aria-busy={isLoading || undefined}
      {...props}
    >
      {isLoading && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
      {isLoading ? <span className="sr-only">{loadingLabel}</span> : null}
      {children}
    </button>
  );
});
