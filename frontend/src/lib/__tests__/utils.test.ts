import { describe, expect, it } from 'vitest';
import {
  formatCount,
  formatInteraction,
  formatRating,
  formatRuntime,
  posterFallbackGradient,
  youTubeIdFrom,
} from '../utils';

describe('formatRuntime', () => {
  it('renders hours and minutes', () => {
    expect(formatRuntime(169)).toBe('2h 49m');
  });

  it('omits the hour component below sixty minutes', () => {
    expect(formatRuntime(45)).toBe('45m');
  });

  it('omits the minute component on an exact hour', () => {
    expect(formatRuntime(120)).toBe('2h');
  });

  it('renders a dash for missing or nonsensical values', () => {
    expect(formatRuntime(null)).toBe('—');
    expect(formatRuntime(0)).toBe('—');
    expect(formatRuntime(-30)).toBe('—');
  });
});

describe('formatRating', () => {
  it('always shows one decimal place', () => {
    expect(formatRating(8)).toBe('8.0');
    expect(formatRating(7.65)).toBe('7.7');
  });

  it('distinguishes a genuine zero from a missing value', () => {
    expect(formatRating(0)).toBe('0.0');
    expect(formatRating(null)).toBe('—');
    expect(formatRating(Number.NaN)).toBe('—');
  });
});

describe('formatCount', () => {
  it('leaves small numbers alone', () => {
    expect(formatCount(999)).toBe('999');
  });

  it('abbreviates thousands and millions', () => {
    expect(formatCount(1500)).toBe('1.5K');
    expect(formatCount(34000)).toBe('34K');
    expect(formatCount(2_400_000)).toBe('2.4M');
  });

  it('treats zero and null as zero', () => {
    expect(formatCount(0)).toBe('0');
    expect(formatCount(null)).toBe('0');
  });
});

describe('youTubeIdFrom', () => {
  it('extracts the id from the standard watch URL', () => {
    expect(youTubeIdFrom('https://www.youtube.com/watch?v=zSWdZVtXT7E')).toBe('zSWdZVtXT7E');
  });

  it('extracts the id from a short link', () => {
    expect(youTubeIdFrom('https://youtu.be/zSWdZVtXT7E')).toBe('zSWdZVtXT7E');
  });

  it('extracts the id from an embed URL', () => {
    expect(youTubeIdFrom('https://www.youtube.com/embed/zSWdZVtXT7E')).toBe('zSWdZVtXT7E');
  });

  it('returns null for anything that is not a YouTube link', () => {
    expect(youTubeIdFrom('https://vimeo.com/12345')).toBeNull();
    expect(youTubeIdFrom(null)).toBeNull();
    expect(youTubeIdFrom('')).toBeNull();
  });
});

describe('formatInteraction', () => {
  it('maps known interaction types to prose', () => {
    expect(formatInteraction('WATCHED_TRAILER')).toBe('Watched the trailer');
    expect(formatInteraction('COMPLETED')).toBe('Finished watching');
  });

  it('degrades gracefully for an unrecognised type', () => {
    expect(formatInteraction('SOME_NEW_EVENT')).toBe('some new event');
  });
});

describe('posterFallbackGradient', () => {
  it('is deterministic, so a film keeps the same placeholder', () => {
    expect(posterFallbackGradient('Interstellar')).toBe(posterFallbackGradient('Interstellar'));
  });

  it('distinguishes different titles', () => {
    expect(posterFallbackGradient('Interstellar')).not.toBe(posterFallbackGradient('Inception'));
  });
});
