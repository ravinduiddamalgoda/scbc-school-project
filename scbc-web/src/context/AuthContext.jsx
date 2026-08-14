import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { onSessionExpired, primeCsrfToken } from '@/lib/api';
import { auth as authApi } from '@/lib/resources';

const AuthContext = createContext(null);

const NO_PRIVILEGE = { select: false, insert: false, update: false, delete: false };

/**
 * Holds the signed-in user and the privilege matrix for the session.
 *
 * On mount it asks the server who the caller is, which restores the session
 * after a page refresh without keeping any credential in localStorage.
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [status, setStatus] = useState('loading'); // loading | authenticated | anonymous

  const bootstrap = useCallback(async () => {
    // Prime the CSRF cookie first so the login POST has a token to send.
    await primeCsrfToken();

    try {
      const me = await authApi.me();
      setUser(me);
      setStatus('authenticated');
    } catch {
      // A 401 here simply means "not signed in yet".
      setUser(null);
      setStatus('anonymous');
    }
  }, []);

  useEffect(() => {
    bootstrap();
  }, [bootstrap]);

  // Any 401 from a later request means the session lapsed server-side.
  useEffect(
    () =>
      onSessionExpired(() => {
        setUser(null);
        setStatus('anonymous');
      }),
    [],
  );

  const login = useCallback(async (username, password) => {
    const me = await authApi.login(username, password);
    setUser(me);
    setStatus('authenticated');
    return me;
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      setUser(null);
      setStatus('anonymous');
      // A fresh CSRF token for the next sign-in.
      await primeCsrfToken();
    }
  }, []);

  /** Re-reads the current user, e.g. after a profile edit. */
  const refresh = useCallback(async () => {
    const me = await authApi.me();
    setUser(me);
    return me;
  }, []);

  /**
   * Privileges for one module. Always returns an object, so callers can write
   * `can('User').insert` without guarding for undefined.
   */
  const can = useCallback(
    (moduleName) => user?.privileges?.[moduleName] ?? NO_PRIVILEGE,
    [user],
  );

  /**
   * Whether the account holds any of the named roles.
   *
   * A few actions are gated on the role rather than on the privilege matrix -
   * entering marks is open to any teacher, which is a fact about being a
   * teacher rather than a right the Principal grants per module. The server
   * makes the same check; this only decides what the screen offers.
   */
  const hasRole = useCallback(
    (...names) => {
      const held = (user?.roles ?? []).map((role) => String(role).toLowerCase());
      // The built-in Admin account holds every right, as it does server-side.
      if (user?.username?.toLowerCase() === 'admin') return true;
      return names.some((name) => held.includes(String(name).toLowerCase()));
    },
    [user],
  );

  const value = useMemo(
    () => ({
      user,
      status,
      isAuthenticated: status === 'authenticated',
      isLoading: status === 'loading',
      login,
      logout,
      refresh,
      can,
      hasRole,
    }),
    [user, status, login, logout, refresh, can, hasRole],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider.');
  }
  return context;
}
