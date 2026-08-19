import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SearchBar } from '../SearchBar';
import { renderWithProviders } from '@/test/render';
import { movieService } from '@/services/movies';

vi.mock('@/services/movies', async () => {
  const actual = await vi.importActual<typeof import('@/services/movies')>('@/services/movies');
  return { ...actual, movieService: { ...actual.movieService, suggest: vi.fn() } };
});

const suggestMock = vi.mocked(movieService.suggest);

describe('SearchBar', () => {
  beforeEach(() => {
    suggestMock.mockReset();
    suggestMock.mockResolvedValue([
      { id: 1, title: 'Interstellar', slug: 'interstellar-2014', posterUrl: null, releaseYear: 2014 },
      { id: 2, title: 'Inception', slug: 'inception-2010', posterUrl: null, releaseYear: 2010 },
    ]);
  });

  it('exposes the input as an accessible combobox', () => {
    renderWithProviders(<SearchBar />);

    const input = screen.getByRole('combobox', { name: /search movies/i });
    expect(input).toHaveAttribute('aria-expanded', 'false');
  });

  it('does not query the API for a single character', async () => {
    renderWithProviders(<SearchBar />);

    await userEvent.type(screen.getByRole('combobox'), 'i');
    await new Promise((resolve) => setTimeout(resolve, 500));

    expect(suggestMock).not.toHaveBeenCalled();
  });

  it('issues one request after typing settles, not one per keystroke', async () => {
    renderWithProviders(<SearchBar />);

    await userEvent.type(screen.getByRole('combobox'), 'inter');

    await waitFor(() => expect(suggestMock).toHaveBeenCalled(), { timeout: 2000 });
    // Five characters typed, but the debounce collapses them into one call.
    expect(suggestMock).toHaveBeenCalledTimes(1);
    expect(suggestMock).toHaveBeenCalledWith('inter');
  });

  it('renders suggestions as selectable options', async () => {
    renderWithProviders(<SearchBar />);

    await userEvent.type(screen.getByRole('combobox'), 'inter');

    const options = await screen.findAllByRole('option', {}, { timeout: 2000 });
    expect(options).toHaveLength(2);
    expect(screen.getByText('Interstellar')).toBeInTheDocument();
  });

  it('moves the active option with the arrow keys without losing input focus', async () => {
    renderWithProviders(<SearchBar />);
    const input = screen.getByRole('combobox');

    await userEvent.type(input, 'inter');
    await screen.findAllByRole('option', {}, { timeout: 2000 });

    await userEvent.keyboard('{ArrowDown}');

    expect(input).toHaveFocus();
    expect(input).toHaveAttribute('aria-activedescendant');
    expect(screen.getAllByRole('option')[0]).toHaveAttribute('aria-selected', 'true');
  });

  it('shows an empty state when nothing matches', async () => {
    suggestMock.mockResolvedValue([]);
    renderWithProviders(<SearchBar />);

    await userEvent.type(screen.getByRole('combobox'), 'zzzz');

    expect(await screen.findByText(/no films match/i, {}, { timeout: 2000 })).toBeInTheDocument();
  });

  it('closes the suggestion panel on Escape', async () => {
    renderWithProviders(<SearchBar />);
    const input = screen.getByRole('combobox');

    await userEvent.type(input, 'inter');
    await screen.findAllByRole('option', {}, { timeout: 2000 });

    await userEvent.keyboard('{Escape}');

    await waitFor(() => expect(screen.queryByRole('option')).not.toBeInTheDocument());
  });
});
