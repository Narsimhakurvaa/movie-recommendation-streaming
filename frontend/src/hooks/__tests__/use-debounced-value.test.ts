import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useDebouncedValue } from '../use-debounced-value';

/**
 * The debounce is what keeps the search endpoint from receiving one request per
 * keystroke, so its timing behaviour is worth pinning down explicitly.
 */
describe('useDebouncedValue', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('returns the initial value immediately', () => {
    const { result } = renderHook(() => useDebouncedValue('initial', 350));
    expect(result.current).toBe('initial');
  });

  it('withholds the new value until the delay has elapsed', () => {
    const { result, rerender } = renderHook(({ value }) => useDebouncedValue(value, 350), {
      initialProps: { value: 'a' },
    });

    rerender({ value: 'ab' });
    expect(result.current).toBe('a');

    act(() => {
      vi.advanceTimersByTime(349);
    });
    expect(result.current).toBe('a');

    act(() => {
      vi.advanceTimersByTime(1);
    });
    expect(result.current).toBe('ab');
  });

  it('emits only the final value when typing quickly', () => {
    const { result, rerender } = renderHook(({ value }) => useDebouncedValue(value, 350), {
      initialProps: { value: '' },
    });

    // Simulates typing "inter" with 100ms between keystrokes.
    for (const value of ['i', 'in', 'int', 'inte', 'inter']) {
      rerender({ value });
      act(() => {
        vi.advanceTimersByTime(100);
      });
    }

    // Nothing has settled yet: each keystroke reset the timer.
    expect(result.current).toBe('');

    act(() => {
      vi.advanceTimersByTime(350);
    });
    expect(result.current).toBe('inter');
  });

  it('cancels a pending update when unmounted', () => {
    const { rerender, unmount } = renderHook(({ value }) => useDebouncedValue(value, 350), {
      initialProps: { value: 'a' },
    });

    rerender({ value: 'b' });
    unmount();

    // Advancing past the delay must not throw from a stale timer.
    expect(() =>
      act(() => {
        vi.advanceTimersByTime(1000);
      }),
    ).not.toThrow();
  });
});
