import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * Merges class names, resolving Tailwind conflicts so a caller-supplied
 * `className` reliably overrides a component's default.
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}

/** Formats a runtime in minutes as "2h 49m". */
export function formatRuntime(minutes: number | null | undefined): string {
  if (!minutes || minutes <= 0) return '—';
  const hours = Math.floor(minutes / 60);
  const remaining = minutes % 60;
  if (hours === 0) return `${remaining}m`;
  if (remaining === 0) return `${hours}h`;
  return `${hours}h ${remaining}m`;
}

/** Formats a 0-10 provider rating to one decimal place. */
export function formatRating(rating: number | null | undefined): string {
  if (rating === null || rating === undefined || Number.isNaN(rating)) return '—';
  return rating.toFixed(1);
}

/** Compact vote counts: 34000 becomes "34K". */
export function formatCount(count: number | null | undefined): string {
  if (!count) return '0';
  if (count < 1000) return String(count);
  if (count < 1_000_000) return `${(count / 1000).toFixed(count < 10_000 ? 1 : 0)}K`;
  return `${(count / 1_000_000).toFixed(1)}M`;
}

/** Human-readable date, e.g. "7 November 2014". */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleDateString(undefined, {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  });
}

/** Relative time, e.g. "3 days ago". */
export function formatRelativeTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '—';

  const seconds = Math.round((Date.now() - date.getTime()) / 1000);
  const formatter = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });
  const divisions: Array<[number, Intl.RelativeTimeFormatUnit]> = [
    [60, 'second'],
    [60, 'minute'],
    [24, 'hour'],
    [7, 'day'],
    [4.34524, 'week'],
    [12, 'month'],
    [Number.POSITIVE_INFINITY, 'year'],
  ];

  let duration = seconds;
  for (const [amount, unit] of divisions) {
    if (Math.abs(duration) < amount) {
      return formatter.format(-Math.round(duration), unit);
    }
    duration /= amount;
  }
  return formatter.format(-Math.round(duration), 'year');
}

/** Turns an interaction enum into readable prose. */
export function formatInteraction(type: string): string {
  const labels: Record<string, string> = {
    VIEWED_DETAILS: 'Viewed details',
    WATCHED_TRAILER: 'Watched the trailer',
    STARTED_WATCHING: 'Started watching',
    COMPLETED: 'Finished watching',
    ADDED_TO_WATCHLIST: 'Added to watchlist',
    RATED: 'Rated',
  };
  return labels[type] ?? type.toLowerCase().replace(/_/g, ' ');
}

/** Extracts a YouTube video id from the common URL shapes. */
export function youTubeIdFrom(url: string | null | undefined): string | null {
  if (!url) return null;
  const match = url.match(/(?:youtube\.com\/(?:watch\?v=|embed\/)|youtu\.be\/)([\w-]{11})/);
  return match ? match[1] : null;
}

/** Deterministic placeholder gradient for films with no poster art. */
export function posterFallbackGradient(seed: string): string {
  let hash = 0;
  for (let i = 0; i < seed.length; i += 1) {
    hash = (hash << 5) - hash + seed.charCodeAt(i);
    hash |= 0;
  }
  const hue = Math.abs(hash) % 360;
  return `linear-gradient(145deg, oklch(0.35 0.06 ${hue}), oklch(0.18 0.04 ${(hue + 40) % 360}))`;
}
