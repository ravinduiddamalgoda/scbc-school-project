import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { useTheme } from '@/context/ThemeContext';
import Button from '@/components/ui/Button';
import { TextField } from '@/components/ui/Field';

export default function LoginPage() {
  const { login } = useAuth();
  const { theme, toggle } = useTheme();
  const navigate = useNavigate();
  const location = useLocation();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError(null);

    if (!username.trim() || !password) {
      setError('Enter both your username and password.');
      return;
    }

    setSubmitting(true);
    try {
      await login(username.trim(), password);
      // Return the user to whatever they were trying to reach.
      navigate(location.state?.from ?? '/dashboard', { replace: true });
    } catch (caught) {
      setError(caught.message);
      setPassword('');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-full">
      {/* Brand panel, hidden on small screens where it would push the form down. */}
      <div className="relative hidden w-1/2 flex-col justify-between overflow-hidden bg-brand-700 p-12 lg:flex">
        <div
          className="absolute inset-0 opacity-25"
          style={{
            backgroundImage:
              'radial-gradient(circle at 20% 20%, rgba(255,255,255,.35) 0, transparent 45%), radial-gradient(circle at 80% 70%, rgba(255,255,255,.25) 0, transparent 40%)',
          }}
          aria-hidden="true"
        />

        <div className="relative">
          <span className="flex size-14 items-center justify-center rounded-2xl bg-white/15 text-2xl font-black text-white ring-1 ring-white/25 backdrop-blur">
            S
          </span>
        </div>

        <div className="relative max-w-md">
          <h1 className="text-4xl font-bold leading-tight tracking-tight text-white">
            Sri Chandananda
            <br />
            Buddhist College
          </h1>
          <p className="mt-4 text-base leading-relaxed text-brand-100">
            Staff, student, guardian and access management in one place.
          </p>
        </div>

        <p className="relative text-xs text-brand-200">
          © {new Date().getFullYear()} Sri Chandananda Buddhist College
        </p>
      </div>

      {/* Form panel. */}
      <div className="flex w-full flex-col justify-center px-6 py-12 sm:px-12 lg:w-1/2">
        <div className="absolute right-4 top-4">
          <button
            type="button"
            onClick={toggle}
            className="rounded-lg p-2 text-slate-400 transition hover:bg-slate-200 dark:hover:bg-slate-800"
            aria-label={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}
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
        </div>

        <div className="mx-auto w-full max-w-sm">
          <div className="mb-8 lg:hidden">
            <span className="flex size-12 items-center justify-center rounded-xl bg-brand-600 text-xl font-black text-white">
              S
            </span>
          </div>

          <h2 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-50">
            Sign in
          </h2>
          <p className="mt-1.5 text-sm text-slate-500 dark:text-slate-400">
            Use the credentials issued by the college administration.
          </p>

          <form onSubmit={handleSubmit} className="mt-8 space-y-4" noValidate>
            {error && (
              <div
                role="alert"
                className="flex items-start gap-2.5 rounded-lg bg-negative-50 p-3 text-sm text-negative-600 ring-1 ring-inset ring-negative-500/20 dark:bg-negative-900/25 dark:text-negative-500"
              >
                <svg
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  className="mt-0.5 size-4 shrink-0"
                  aria-hidden="true"
                >
                  <circle cx="12" cy="12" r="9" />
                  <path d="M12 8v5m0 3h.01" />
                </svg>
                {error}
              </div>
            )}

            <TextField
              label="Username"
              name="username"
              autoComplete="username"
              autoFocus
              required
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              placeholder="Admin or your staff number"
            />

            <div className="relative">
              <TextField
                label="Password"
                name="password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                required
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                placeholder="••••••••"
              />

              <button
                type="button"
                onClick={() => setShowPassword((shown) => !shown)}
                className="absolute right-2 top-[1.9rem] rounded-md p-1.5 text-slate-400 transition hover:text-slate-600 dark:hover:text-slate-300"
                aria-label={showPassword ? 'Hide password' : 'Show password'}
              >
                <svg
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.9"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  className="size-4"
                  aria-hidden="true"
                >
                  {showPassword ? (
                    <path d="M2 2l20 20M10.6 10.6a3 3 0 0 0 4.2 4.2M9.4 5.2A10 10 0 0 1 12 5c6.5 0 10 7 10 7a17 17 0 0 1-3.2 4.1M6.2 6.2A17 17 0 0 0 2 12s3.5 7 10 7a10 10 0 0 0 3.5-.6" />
                  ) : (
                    <>
                      <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z" />
                      <circle cx="12" cy="12" r="3" />
                    </>
                  )}
                </svg>
              </button>
            </div>

            <Button type="submit" size="lg" loading={submitting} className="w-full">
              {submitting ? 'Signing in' : 'Sign in'}
            </Button>
          </form>

          <p className="mt-8 text-center text-xs text-slate-400 dark:text-slate-500">
            Trouble signing in? Contact the system administrator.
          </p>
        </div>
      </div>
    </div>
  );
}
