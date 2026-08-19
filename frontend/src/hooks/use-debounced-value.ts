import { useEffect, useState } from 'react';

/**
 * Returns a value that only updates after it has stopped changing for `delay`.
 *
 * Used by the search box so typing does not issue a request per keystroke: the
 * query fires once the user pauses, cutting a ten-character search from ten
 * requests to one.
 */
export function useDebouncedValue<T>(value: T, delay = 350): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delay);
    // Clearing on every change is what actually produces the debounce.
    return () => window.clearTimeout(timer);
  }, [value, delay]);

  return debounced;
}
