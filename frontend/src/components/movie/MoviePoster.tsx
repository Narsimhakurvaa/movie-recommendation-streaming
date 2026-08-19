import { useState } from 'react';
import { Film } from 'lucide-react';
import { cn, posterFallbackGradient } from '@/lib/utils';

interface MoviePosterProps {
  src: string | null;
  title: string;
  className?: string;
  /** Skip lazy loading for above-the-fold art such as the hero. */
  priority?: boolean;
}

/**
 * Poster image with lazy loading and a graceful fallback.
 *
 * Seeded catalogue entries point at image paths that may not resolve, and real
 * providers occasionally return dead links, so a broken poster has to look
 * deliberate rather than broken. The fallback derives a stable gradient from
 * the title, giving every film a consistent identity across renders.
 */
export function MoviePoster({ src, title, className, priority = false }: MoviePosterProps) {
  const [failed, setFailed] = useState(false);
  const showFallback = !src || failed;

  if (showFallback) {
    return (
      <div
        className={cn(
          'flex items-center justify-center overflow-hidden rounded-[var(--radius-card)]',
          className,
        )}
        style={{ background: posterFallbackGradient(title) }}
        // Decorative: the adjacent title already conveys the film.
        role="img"
        aria-label={`${title} (no poster available)`}
      >
        <Film className="h-8 w-8 text-white/40" aria-hidden="true" />
      </div>
    );
  }

  return (
    <img
      src={src}
      alt={`${title} poster`}
      className={cn('object-cover', className)}
      loading={priority ? 'eager' : 'lazy'}
      decoding="async"
      // Reserves layout space, preventing shift as posters stream in.
      width={500}
      height={750}
      onError={() => setFailed(true)}
    />
  );
}
