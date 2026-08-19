import { useEffect, useId, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Loader2, Search, X } from 'lucide-react';
import { movieService } from '@/services/movies';
import { useDebouncedValue } from '@/hooks/use-debounced-value';
import { MoviePoster } from '@/components/movie/MoviePoster';

/**
 * Global search with debounced suggestions.
 *
 * Implements the combobox pattern: the input owns focus throughout while the
 * arrow keys move an `aria-activedescendant` marker through the list. Moving
 * DOM focus into the options instead would fight the user's typing.
 *
 * Requests only fire after a 350ms pause and only from two characters, so a
 * ten-character search costs one request rather than ten.
 */
export function SearchBar({ onNavigate }: { onNavigate?: () => void }) {
  const [term, setTerm] = useState('');
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();
  const listId = useId();

  const debouncedTerm = useDebouncedValue(term, 350);
  const canSearch = debouncedTerm.trim().length >= 2;

  const { data: suggestions = [], isFetching } = useQuery({
    queryKey: ['movie-suggestions', debouncedTerm],
    queryFn: () => movieService.suggest(debouncedTerm),
    enabled: canSearch,
    staleTime: 60_000,
  });

  // Close when focus or a click leaves the widget entirely.
  useEffect(() => {
    const handlePointerDown = (event: MouseEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', handlePointerDown);
    return () => document.removeEventListener('mousedown', handlePointerDown);
  }, []);

  // Reset the highlight whenever the option list changes underneath it.
  // Adjusting during render (rather than in an effect) avoids a second render
  // pass in which a stale index could briefly point at the wrong option.
  const [lastTerm, setLastTerm] = useState(debouncedTerm);
  if (lastTerm !== debouncedTerm) {
    setLastTerm(debouncedTerm);
    setActiveIndex(-1);
  }

  const submitSearch = (query: string) => {
    if (!query.trim()) return;
    setOpen(false);
    inputRef.current?.blur();
    onNavigate?.();
    navigate(`/search?q=${encodeURIComponent(query.trim())}`);
  };

  const goToMovie = (id: number) => {
    setOpen(false);
    setTerm('');
    onNavigate?.();
    navigate(`/movies/${id}`);
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setOpen(true);
      setActiveIndex((index) => (index + 1) % Math.max(suggestions.length, 1));
      return;
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault();
      setActiveIndex((index) => (index <= 0 ? suggestions.length - 1 : index - 1));
      return;
    }
    if (event.key === 'Enter') {
      event.preventDefault();
      const active = suggestions[activeIndex];
      if (active) goToMovie(active.id);
      else submitSearch(term);
      return;
    }
    if (event.key === 'Escape') {
      setOpen(false);
      setActiveIndex(-1);
    }
  };

  const showPanel = open && canSearch;
  const activeId = activeIndex >= 0 ? `${listId}-option-${activeIndex}` : undefined;

  return (
    <div ref={containerRef} className="relative w-full">
      <div className="relative">
        <Search
          className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--text-muted)]"
          aria-hidden="true"
        />
        <input
          ref={inputRef}
          type="search"
          role="combobox"
          aria-expanded={showPanel}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={activeId}
          aria-label="Search movies"
          placeholder="Search films, directors, genres…"
          value={term}
          onChange={(event) => {
            setTerm(event.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          onKeyDown={handleKeyDown}
          className="h-10 w-full rounded-lg border border-[var(--border-subtle)] bg-[var(--surface-sunken)] pl-9 pr-9 text-sm text-[var(--text-primary)] placeholder:text-[var(--text-muted)] focus-visible:outline-2"
        />
        {isFetching && canSearch ? (
          <Loader2
            className="absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 animate-spin text-[var(--text-muted)]"
            aria-hidden="true"
          />
        ) : term ? (
          <button
            type="button"
            onClick={() => {
              setTerm('');
              inputRef.current?.focus();
            }}
            aria-label="Clear search"
            className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-[var(--text-muted)] hover:text-[var(--text-primary)]"
          >
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        ) : null}
      </div>

      {showPanel ? (
        <div className="absolute left-0 right-0 top-12 z-50 overflow-hidden rounded-lg border border-[var(--border-subtle)] bg-[var(--surface-overlay)] shadow-xl">
          <ul id={listId} role="listbox" aria-label="Search suggestions" className="max-h-80 overflow-y-auto">
            {suggestions.length === 0 && !isFetching ? (
              <li className="px-4 py-6 text-center text-sm text-[var(--text-muted)]">
                No films match “{debouncedTerm}”
              </li>
            ) : (
              suggestions.map((suggestion, index) => (
                <li
                  key={suggestion.id}
                  id={`${listId}-option-${index}`}
                  role="option"
                  aria-selected={index === activeIndex}
                  onMouseEnter={() => setActiveIndex(index)}
                  onMouseDown={(event) => {
                    // mousedown, not click: blur would close the panel first.
                    event.preventDefault();
                    goToMovie(suggestion.id);
                  }}
                  className={`flex cursor-pointer items-center gap-3 px-3 py-2 ${
                    index === activeIndex ? 'bg-[var(--surface-sunken)]' : ''
                  }`}
                >
                  <MoviePoster
                    src={suggestion.posterUrl}
                    title={suggestion.title}
                    className="h-14 w-10 shrink-0 rounded"
                  />
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">{suggestion.title}</p>
                    <p className="text-xs text-[var(--text-muted)]">
                      {suggestion.releaseYear ?? 'TBA'}
                    </p>
                  </div>
                </li>
              ))
            )}
          </ul>
          <button
            type="button"
            onMouseDown={(event) => {
              event.preventDefault();
              submitSearch(term);
            }}
            className="w-full border-t border-[var(--border-subtle)] px-4 py-2.5 text-left text-sm font-medium text-[var(--accent)] hover:bg-[var(--surface-sunken)]"
          >
            See all results for “{term}”
          </button>
        </div>
      ) : null}
    </div>
  );
}
