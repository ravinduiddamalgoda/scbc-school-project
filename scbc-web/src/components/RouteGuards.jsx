import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import Spinner from '@/components/ui/Spinner';

function FullPageSpinner() {
  return (
    <div className="flex h-full items-center justify-center text-brand-500">
      <Spinner className="size-8" label="Loading" />
    </div>
  );
}

/**
 * Blocks a route until the session is confirmed.
 *
 * While the initial /auth/me call is in flight nothing is rendered, which
 * prevents a signed-in user from seeing the login screen flash on refresh.
 */
export function RequireAuth({ children }) {
  const { isAuthenticated, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) return <FullPageSpinner />;

  if (!isAuthenticated) {
    // Remember where they were headed so login can send them back.
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return children;
}

/**
 * Gates a route on a module privilege.
 *
 * This is convenience, not security: the server re-checks the same privilege
 * on every request, so bypassing this in the browser gains nothing.
 */
export function RequirePrivilege({ module, action = 'select', children }) {
  const { can, isLoading } = useAuth();

  if (isLoading) return <FullPageSpinner />;

  if (!can(module)[action]) {
    return <Navigate to="/forbidden" replace state={{ module }} />;
  }

  return children;
}

/** Sends an already-authenticated visitor away from the login screen. */
export function RedirectIfAuthenticated({ children }) {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) return <FullPageSpinner />;
  if (isAuthenticated) return <Navigate to="/dashboard" replace />;

  return children;
}
