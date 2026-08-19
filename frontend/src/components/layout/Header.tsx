import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import {
  Bookmark, Clapperboard, History, LayoutDashboard, LogOut, Menu, Moon,
  Settings, Sparkles, Sun, User as UserIcon, X,
} from 'lucide-react';
import { useAuth } from '@/hooks/use-auth';
import { useTheme } from '@/hooks/use-theme';
import { Button } from '@/components/ui/Button';
import { cn } from '@/lib/utils';
import { SearchBar } from './SearchBar';

const NAV_LINKS = [
  { to: '/movies', label: 'Discover' },
  { to: '/recommendations', label: 'For You' },
  { to: '/genres', label: 'Genres' },
];

/**
 * Application header: brand, primary navigation, search and account menu.
 *
 * The mobile layout is a genuine alternative rather than a compressed desktop
 * one: navigation collapses into a disclosure panel and search moves onto its
 * own row, so neither is cramped on a narrow viewport.
 */
export function Header() {
  const { user, isAuthenticated, isAdmin, logout } = useAuth();
  const { resolvedTheme, toggleTheme } = useTheme();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [accountOpen, setAccountOpen] = useState(false);
  const accountRef = useRef<HTMLDivElement>(null);
  const location = useLocation();
  const navigate = useNavigate();

  // A route change should never leave a menu hanging open. Derived during
  // render so the menus are already closed in the same commit as the new
  // route, instead of flashing open for one frame.
  const [lastPath, setLastPath] = useState(location.pathname);
  if (lastPath !== location.pathname) {
    setLastPath(location.pathname);
    setMobileOpen(false);
    setAccountOpen(false);
  }

  useEffect(() => {
    const onPointerDown = (event: MouseEvent) => {
      if (!accountRef.current?.contains(event.target as Node)) setAccountOpen(false);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setAccountOpen(false);
    };
    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, []);

  const handleLogout = async () => {
    await logout();
    navigate('/');
  };

  const navLinkClass = ({ isActive }: { isActive: boolean }) =>
    cn(
      'rounded-md px-3 py-2 text-sm font-medium transition-colors',
      isActive
        ? 'text-[var(--accent)]'
        : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)]',
    );

  return (
    <header className="sticky top-0 z-40 border-b border-[var(--border-subtle)] bg-[var(--surface)]/85 backdrop-blur-lg">
      <div className="mx-auto flex h-16 max-w-7xl items-center gap-4 px-4 sm:px-6 lg:px-8">
        <Link
          to="/"
          className="flex shrink-0 items-center gap-2 focus-visible:outline-2 focus-visible:outline-offset-4"
          aria-label="CineVault home"
        >
          <Clapperboard className="h-6 w-6 text-[var(--accent)]" aria-hidden="true" />
          <span className="font-[family-name:var(--font-display)] text-lg font-bold tracking-tight">
            Cine<span className="text-[var(--accent)]">Vault</span>
          </span>
        </Link>

        <nav className="hidden items-center gap-1 md:flex" aria-label="Primary">
          {NAV_LINKS.map((link) => (
            <NavLink key={link.to} to={link.to} className={navLinkClass}>
              {link.label}
            </NavLink>
          ))}
        </nav>

        <div className="ml-auto hidden max-w-sm flex-1 md:block">
          <SearchBar />
        </div>

        <div className="ml-auto flex items-center gap-1 md:ml-0">
          <Button
            variant="ghost"
            size="icon"
            onClick={toggleTheme}
            aria-label={`Switch to ${resolvedTheme === 'dark' ? 'light' : 'dark'} theme`}
          >
            {resolvedTheme === 'dark' ? (
              <Sun className="h-5 w-5" aria-hidden="true" />
            ) : (
              <Moon className="h-5 w-5" aria-hidden="true" />
            )}
          </Button>

          {isAuthenticated ? (
            <div ref={accountRef} className="relative">
              <button
                type="button"
                onClick={() => setAccountOpen((open) => !open)}
                aria-expanded={accountOpen}
                aria-haspopup="menu"
                aria-label="Account menu"
                className="flex h-9 w-9 items-center justify-center rounded-full bg-[var(--accent)] text-sm font-bold text-[var(--accent-contrast)] focus-visible:outline-2 focus-visible:outline-offset-2"
              >
                {user?.displayName?.charAt(0).toUpperCase() ?? '?'}
              </button>

              {accountOpen ? (
                <div
                  role="menu"
                  aria-label="Account"
                  className="absolute right-0 top-11 w-56 overflow-hidden rounded-lg border border-[var(--border-subtle)] bg-[var(--surface-overlay)] py-1 shadow-xl"
                >
                  <div className="border-b border-[var(--border-subtle)] px-4 py-3">
                    <p className="truncate text-sm font-semibold">{user?.displayName}</p>
                    <p className="truncate text-xs text-[var(--text-muted)]">{user?.email}</p>
                  </div>
                  <MenuLink to="/watchlist" icon={Bookmark} label="Watchlist" />
                  <MenuLink to="/history" icon={History} label="History" />
                  <MenuLink to="/recommendations" icon={Sparkles} label="For You" />
                  <MenuLink to="/profile" icon={UserIcon} label="Profile" />
                  <MenuLink to="/settings" icon={Settings} label="Settings" />
                  {isAdmin ? (
                    <MenuLink to="/admin" icon={LayoutDashboard} label="Admin" />
                  ) : null}
                  <button
                    type="button"
                    role="menuitem"
                    onClick={handleLogout}
                    className="flex w-full items-center gap-2 border-t border-[var(--border-subtle)] px-4 py-2.5 text-left text-sm text-[var(--text-secondary)] hover:bg-[var(--surface-sunken)] hover:text-[var(--text-primary)]"
                  >
                    <LogOut className="h-4 w-4" aria-hidden="true" />
                    Sign out
                  </button>
                </div>
              ) : null}
            </div>
          ) : (
            <div className="hidden items-center gap-2 sm:flex">
              <Button variant="ghost" size="sm" onClick={() => navigate('/login')}>
                Sign in
              </Button>
              <Button size="sm" onClick={() => navigate('/register')}>
                Join
              </Button>
            </div>
          )}

          <Button
            variant="ghost"
            size="icon"
            className="md:hidden"
            onClick={() => setMobileOpen((open) => !open)}
            aria-expanded={mobileOpen}
            aria-controls="mobile-navigation"
            aria-label={mobileOpen ? 'Close menu' : 'Open menu'}
          >
            {mobileOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </Button>
        </div>
      </div>

      {mobileOpen ? (
        <div
          id="mobile-navigation"
          className="border-t border-[var(--border-subtle)] bg-[var(--surface)] px-4 py-3 md:hidden"
        >
          <div className="mb-3">
            <SearchBar onNavigate={() => setMobileOpen(false)} />
          </div>
          <nav className="flex flex-col gap-1" aria-label="Mobile">
            {NAV_LINKS.map((link) => (
              <NavLink key={link.to} to={link.to} className={navLinkClass}>
                {link.label}
              </NavLink>
            ))}
            {!isAuthenticated ? (
              <div className="mt-2 flex gap-2">
                <Button variant="secondary" className="flex-1" onClick={() => navigate('/login')}>
                  Sign in
                </Button>
                <Button className="flex-1" onClick={() => navigate('/register')}>
                  Join
                </Button>
              </div>
            ) : null}
          </nav>
        </div>
      ) : null}
    </header>
  );
}

function MenuLink({
  to,
  icon: Icon,
  label,
}: {
  to: string;
  icon: typeof Bookmark;
  label: string;
}) {
  return (
    <Link
      to={to}
      role="menuitem"
      className="flex items-center gap-2 px-4 py-2.5 text-sm text-[var(--text-secondary)] hover:bg-[var(--surface-sunken)] hover:text-[var(--text-primary)]"
    >
      <Icon className="h-4 w-4" aria-hidden="true" />
      {label}
    </Link>
  );
}
