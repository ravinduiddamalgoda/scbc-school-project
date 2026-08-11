import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { useTheme } from '@/context/ThemeContext';
import { useToast } from '@/context/ToastContext';
import Avatar from '@/components/ui/Avatar';
import { NavIcon } from './navigation';

export default function Topbar({ onOpenSidebar }) {
  const { user, logout } = useAuth();
  const { theme, toggle } = useTheme();
  const toast = useToast();
  const navigate = useNavigate();

  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef(null);

  // Close the profile menu on an outside click or Escape.
  useEffect(() => {
    if (!menuOpen) return undefined;

    const handlePointerDown = (event) => {
      if (!menuRef.current?.contains(event.target)) setMenuOpen(false);
    };
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') setMenuOpen(false);
    };

    document.addEventListener('mousedown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [menuOpen]);

  const handleLogout = async () => {
    setMenuOpen(false);
    await logout();
    toast.info('You have been signed out.');
    navigate('/login', { replace: true });
  };

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b border-slate-200 bg-white/85 px-4 backdrop-blur-md sm:px-6 dark:border-slate-800 dark:bg-slate-900/85">
      <button
        type="button"
        onClick={onOpenSidebar}
        className="rounded-lg p-2 text-slate-500 transition hover:bg-slate-100 lg:hidden dark:text-slate-400 dark:hover:bg-slate-800"
        aria-label="Open navigation menu"
      >
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          className="size-5"
        >
          <path d="M4 6h16M4 12h16M4 18h16" />
        </svg>
      </button>

      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-semibold text-slate-800 dark:text-slate-100">
          Sri Chandananda Buddhist College
        </p>
        <p className="hidden truncate text-xs text-slate-500 sm:block dark:text-slate-400">
          School Management System
        </p>
      </div>

      <button
        type="button"
        onClick={toggle}
        className="rounded-lg p-2 text-slate-500 transition hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-800"
        aria-label={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}
        title={theme === 'dark' ? 'Light theme' : 'Dark theme'}
      >
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.9"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="size-5"
          aria-hidden="true"
        >
          {theme === 'dark' ? (
            <>
              <circle cx="12" cy="12" r="4" />
              <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
            </>
          ) : (
            <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z" />
          )}
        </svg>
      </button>

      <div className="relative" ref={menuRef}>
        <button
          type="button"
          onClick={() => setMenuOpen((open) => !open)}
          className="flex items-center gap-2 rounded-lg py-1 pl-1 pr-2 transition hover:bg-slate-100 dark:hover:bg-slate-800"
          aria-haspopup="menu"
          aria-expanded={menuOpen}
        >
          <Avatar src={user?.photo} name={user?.username} size="sm" />
          <span className="hidden max-w-32 truncate text-sm font-semibold text-slate-700 sm:block dark:text-slate-200">
            {user?.username}
          </span>
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            className="size-4 text-slate-400"
            aria-hidden="true"
          >
            <path d="m6 9 6 6 6-6" />
          </svg>
        </button>

        {menuOpen && (
          <div
            role="menu"
            className="animate-rise absolute right-0 mt-2 w-60 overflow-hidden rounded-panel bg-white shadow-raised ring-1 ring-slate-900/5 dark:bg-slate-900 dark:ring-white/10"
          >
            <div className="border-b border-slate-100 px-4 py-3 dark:border-slate-800">
              <p className="truncate text-sm font-semibold text-slate-800 dark:text-slate-100">
                {user?.username}
              </p>
              <p className="truncate text-xs text-slate-500 dark:text-slate-400">{user?.email}</p>
              {user?.roles?.length > 0 && (
                <p className="mt-1.5 truncate text-xs font-medium text-brand-600 dark:text-brand-400">
                  {user.roles.join(', ')}
                </p>
              )}
            </div>

            <Link
              to="/profile"
              role="menuitem"
              onClick={() => setMenuOpen(false)}
              className="flex items-center gap-3 px-4 py-2.5 text-sm text-slate-600 transition hover:bg-slate-50 dark:text-slate-300 dark:hover:bg-slate-800"
            >
              <span className="text-slate-400">
                <NavIcon name="settings" className="size-4" />
              </span>
              My profile
            </Link>

            <button
              type="button"
              role="menuitem"
              onClick={handleLogout}
              className="flex w-full items-center gap-3 px-4 py-2.5 text-sm text-negative-600 transition hover:bg-negative-50 dark:text-negative-500 dark:hover:bg-negative-900/25"
            >
              <NavIcon name="logout" className="size-4" />
              Sign out
            </button>
          </div>
        )}
      </div>
    </header>
  );
}
