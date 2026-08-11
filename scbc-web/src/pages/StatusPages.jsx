import { Link, useLocation } from 'react-router-dom';
import Button from '@/components/ui/Button';

function StatusScreen({ code, title, message, action }) {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center px-6 text-center">
      <p className="text-6xl font-black tracking-tight text-brand-500/25 dark:text-brand-500/20">
        {code}
      </p>
      <h1 className="mt-2 text-xl font-bold tracking-tight text-slate-900 dark:text-slate-50">
        {title}
      </h1>
      <p className="mt-2 max-w-md text-sm leading-relaxed text-slate-500 dark:text-slate-400">
        {message}
      </p>
      <div className="mt-6">{action}</div>
    </div>
  );
}

export function NotFoundPage() {
  return (
    <StatusScreen
      code="404"
      title="Page not found"
      message="The page you asked for does not exist, or it has moved."
      action={
        <Link to="/dashboard">
          <Button>Back to dashboard</Button>
        </Link>
      }
    />
  );
}

export function ForbiddenPage() {
  const location = useLocation();
  const moduleName = location.state?.module;

  return (
    <StatusScreen
      code="403"
      title="You do not have access"
      message={
        moduleName
          ? `Your role does not include permission to view the ${moduleName} module. Ask an administrator to grant it.`
          : 'Your role does not include permission for that page. Ask an administrator to grant it.'
      }
      action={
        <Link to="/dashboard">
          <Button>Back to dashboard</Button>
        </Link>
      }
    />
  );
}
