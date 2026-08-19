import type { AuthenticatedUser } from '@/types/api';

/**
 * Persists the session across page reloads.
 *
 * ## Why localStorage
 * Tokens in `localStorage` are readable by any script on the page, so an XSS
 * flaw exposes them. The stronger alternative is an httpOnly, SameSite cookie,
 * which script cannot read.
 *
 * That is the right choice for a first-party deployment and is what this design
 * would move to in production; it is not used here because the API is
 * deliberately stateless and header-authenticated, which keeps it usable from
 * non-browser clients and avoids reintroducing CSRF handling.
 *
 * The trade-off is mitigated by: a strict Content-Security-Policy, React's
 * automatic escaping, 15-minute access tokens, and single-use refresh tokens
 * with reuse detection - so a stolen token has a narrow window and using it
 * twice locks the attacker out along with the victim.
 */

const ACCESS_TOKEN_KEY = 'cinevault.accessToken';
const REFRESH_TOKEN_KEY = 'cinevault.refreshToken';
const USER_KEY = 'cinevault.user';

interface Session {
  accessToken: string;
  refreshToken: string;
  user: AuthenticatedUser;
}

/** Guards against storage being unavailable (private mode, disabled cookies). */
function safeRead(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

function safeWrite(key: string, value: string): void {
  try {
    window.localStorage.setItem(key, value);
  } catch {
    // Storage full or blocked: the session simply will not survive a reload.
  }
}

function safeRemove(key: string): void {
  try {
    window.localStorage.removeItem(key);
  } catch {
    // Nothing useful to do.
  }
}

export const tokenStore = {
  getAccessToken: (): string | null => safeRead(ACCESS_TOKEN_KEY),

  getRefreshToken: (): string | null => safeRead(REFRESH_TOKEN_KEY),

  getUser: (): AuthenticatedUser | null => {
    const raw = safeRead(USER_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as AuthenticatedUser;
    } catch {
      // Corrupted entry: discard rather than crash on every read.
      safeRemove(USER_KEY);
      return null;
    }
  },

  set: (session: Session): void => {
    safeWrite(ACCESS_TOKEN_KEY, session.accessToken);
    safeWrite(REFRESH_TOKEN_KEY, session.refreshToken);
    safeWrite(USER_KEY, JSON.stringify(session.user));
  },

  clear: (): void => {
    safeRemove(ACCESS_TOKEN_KEY);
    safeRemove(REFRESH_TOKEN_KEY);
    safeRemove(USER_KEY);
  },

  isAuthenticated: (): boolean => Boolean(safeRead(ACCESS_TOKEN_KEY)),
};
