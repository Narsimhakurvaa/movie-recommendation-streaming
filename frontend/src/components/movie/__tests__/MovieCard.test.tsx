import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MovieCard } from '../MovieCard';
import { buildMovie, renderWithProviders } from '@/test/render';

describe('MovieCard', () => {
  it('shows the title, year and provider rating', () => {
    renderWithProviders(<MovieCard movie={buildMovie()} />);

    expect(screen.getByRole('link', { name: /Interstellar, 2014/i })).toBeInTheDocument();
    expect(screen.getByText('8.4')).toBeInTheDocument();
    expect(screen.getByText(/2014/)).toBeInTheDocument();
  });

  it('links through to the detail page', () => {
    renderWithProviders(<MovieCard movie={buildMovie({ id: 42 })} />);

    const links = screen.getAllByRole('link');
    expect(links.some((link) => link.getAttribute('href') === '/movies/42')).toBe(true);
  });

  it('exposes the watchlist control as a toggle button', () => {
    renderWithProviders(<MovieCard movie={buildMovie()} onToggleWatchlist={vi.fn()} />);

    const button = screen.getByRole('button', { name: /add interstellar to watchlist/i });
    expect(button).toHaveAttribute('aria-pressed', 'false');
  });

  it('reflects saved state in the accessible name and pressed state', () => {
    renderWithProviders(
      <MovieCard movie={buildMovie({ inWatchlist: true })} onToggleWatchlist={vi.fn()} />,
    );

    const button = screen.getByRole('button', { name: /remove interstellar from watchlist/i });
    expect(button).toHaveAttribute('aria-pressed', 'true');
  });

  it('invokes the toggle handler with the movie', async () => {
    const onToggle = vi.fn();
    const movie = buildMovie();
    renderWithProviders(<MovieCard movie={movie} onToggleWatchlist={onToggle} />);

    await userEvent.click(screen.getByRole('button', { name: /add interstellar/i }));

    expect(onToggle).toHaveBeenCalledExactlyOnceWith(movie);
  });

  it('does not render a watchlist control when no handler is supplied', () => {
    renderWithProviders(<MovieCard movie={buildMovie()} />);

    expect(screen.queryByRole('button', { name: /watchlist/i })).not.toBeInTheDocument();
  });

  it('renders the recommendation reason when one is given', () => {
    renderWithProviders(
      <MovieCard movie={buildMovie()} reason="Because you liked Inception" />,
    );

    expect(screen.getByText('Because you liked Inception')).toBeInTheDocument();
  });

  it('shows the viewer’s own rating when they have rated it', () => {
    renderWithProviders(<MovieCard movie={buildMovie({ userRating: 5 })} />);

    expect(screen.getByText('5')).toBeInTheDocument();
  });

  it('falls back to a placeholder when the film has no poster', () => {
    renderWithProviders(<MovieCard movie={buildMovie({ posterUrl: null })} />);

    expect(screen.getByRole('img', { name: /no poster available/i })).toBeInTheDocument();
  });

  it('disables the toggle while a request is in flight', () => {
    renderWithProviders(
      <MovieCard movie={buildMovie()} onToggleWatchlist={vi.fn()} isTogglingWatchlist />,
    );

    expect(screen.getByRole('button', { name: /add interstellar/i })).toBeDisabled();
  });
});
