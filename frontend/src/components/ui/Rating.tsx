import { useState } from 'react';
import { Star } from 'lucide-react';
import { cn } from '@/lib/utils';

interface StarRatingProps {
  value: number | null;
  onChange?: (score: number) => void;
  onClear?: () => void;
  readOnly?: boolean;
  size?: 'sm' | 'md' | 'lg';
  label?: string;
}

const SIZES = { sm: 'h-4 w-4', md: 'h-6 w-6', lg: 'h-8 w-8' } as const;

/**
 * Five-star rating control.
 *
 * Implemented as a radio group rather than a row of buttons: that is what the
 * interaction actually is (one choice from five), and it gives arrow-key
 * navigation and correct screen-reader semantics for free.
 */
export function StarRating({
  value,
  onChange,
  onClear,
  readOnly = false,
  size = 'md',
  label = 'Your rating',
}: StarRatingProps) {
  const [hovered, setHovered] = useState<number | null>(null);
  const displayed = hovered ?? value ?? 0;

  if (readOnly) {
    return (
      <div
        className="flex items-center gap-0.5"
        role="img"
        aria-label={value ? `Rated ${value} out of 5` : 'Not rated'}
      >
        {[1, 2, 3, 4, 5].map((star) => (
          <Star
            key={star}
            className={cn(
              SIZES[size],
              star <= displayed
                ? 'fill-[var(--accent)] text-[var(--accent)]'
                : 'text-[var(--border-strong)]',
            )}
            aria-hidden="true"
          />
        ))}
      </div>
    );
  }

  return (
    <div
      role="radiogroup"
      aria-label={label}
      className="flex items-center gap-1"
      onMouseLeave={() => setHovered(null)}
    >
      {[1, 2, 3, 4, 5].map((star) => {
        const selected = value === star;
        return (
          <button
            key={star}
            type="button"
            role="radio"
            aria-checked={selected}
            aria-label={`${star} star${star === 1 ? '' : 's'}`}
            className="rounded p-0.5 transition-transform hover:scale-110 focus-visible:outline-2"
            onMouseEnter={() => setHovered(star)}
            onFocus={() => setHovered(star)}
            onBlur={() => setHovered(null)}
            onClick={() => {
              // Clicking the current rating clears it, which is the only
              // discoverable way to undo a rating without a second control.
              if (selected && onClear) onClear();
              else onChange?.(star);
            }}
          >
            <Star
              className={cn(
                SIZES[size],
                star <= displayed
                  ? 'fill-[var(--accent)] text-[var(--accent)]'
                  : 'text-[var(--border-strong)]',
              )}
              aria-hidden="true"
            />
          </button>
        );
      })}
      {value ? (
        <span className="ml-2 text-sm text-[var(--text-muted)]">Click again to clear</span>
      ) : null}
    </div>
  );
}
