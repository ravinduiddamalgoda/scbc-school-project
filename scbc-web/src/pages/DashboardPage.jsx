import { useCallback } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { useResource } from '@/hooks/useResource';
import { classes, employees, guardians, students, subjects, users } from '@/lib/resources';
import PageHeader from '@/components/ui/PageHeader';
import { NavIcon } from '@/components/layout/navigation';
import Spinner from '@/components/ui/Spinner';

/**
 * Tiles for the modules that exist today, plus the roadmap items the original
 * dashboard advertised with empty links. Unbuilt tiles are labelled instead of
 * pretending to navigate.
 */
const PLANNED = [
  { label: 'Exams & results', icon: 'award' },
  { label: 'Certificates', icon: 'book' },
  { label: 'Fee structures & invoicing', icon: 'money' },
];

function StatCard({ label, value, loading, icon, to, tone = 'brand' }) {
  const body = (
    <>
      <div className="flex items-start justify-between gap-3">
        <span
          className={[
            'flex size-10 items-center justify-center rounded-xl',
            tone === 'brand'
              ? 'bg-brand-50 text-brand-600 dark:bg-brand-950 dark:text-brand-400'
              : 'bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-400',
          ].join(' ')}
          aria-hidden="true"
        >
          <NavIcon name={icon} className="size-5" />
        </span>

        {to && (
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            className="size-4 text-slate-300 transition group-hover:translate-x-0.5 group-hover:text-brand-500 dark:text-slate-600"
            aria-hidden="true"
          >
            <path d="M5 12h14M13 6l6 6-6 6" />
          </svg>
        )}
      </div>

      <p className="mt-4 text-3xl font-bold tabular-nums tracking-tight text-slate-900 dark:text-slate-50">
        {loading ? <Spinner className="size-6 text-slate-300" /> : value}
      </p>
      <p className="mt-0.5 text-sm font-medium text-slate-500 dark:text-slate-400">{label}</p>
    </>
  );

  const className =
    'group block rounded-panel bg-white p-5 shadow-panel ring-1 ring-slate-900/5 transition hover:shadow-raised dark:bg-slate-900 dark:ring-white/10';

  return to ? (
    <Link to={to} className={className}>
      {body}
    </Link>
  ) : (
    <div className={className}>{body}</div>
  );
}

export default function DashboardPage() {
  const { user, can } = useAuth();

  const canStudents = can('Student').select;
  const canEmployees = can('Employee').select;
  const canGuardians = can('Guardian').select;
  const canUsers = can('User').select;
  const canClasses = can('Class').select;
  const canSubjects = can('Subject').select;
  const canReports = can('Report').select;

  // Each count is its own request so a module the user cannot read simply
  // does not load, rather than failing the whole page.
  const studentList = useResource(useCallback(() => students.list(), []), { enabled: canStudents });
  const employeeList = useResource(useCallback(() => employees.list(), []), {
    enabled: canEmployees,
  });
  const guardianList = useResource(useCallback(() => guardians.list(), []), {
    enabled: canGuardians,
  });
  const userList = useResource(useCallback(() => users.list(), []), { enabled: canUsers });

  // Classes are scoped to an academic year; no argument means the current one.
  const classList = useResource(useCallback(() => classes.list(), []), { enabled: canClasses });
  const subjectList = useResource(useCallback(() => subjects.list(), []), {
    enabled: canSubjects,
  });

  const hour = new Date().getHours();
  const greeting = hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';

  return (
    <>
      <PageHeader
        eyebrow={greeting}
        title={`Welcome back, ${user?.username ?? ''}`}
        description="An overview of the records you have access to."
        icon={<NavIcon name="grid" className="size-5" />}
      />

      <section aria-labelledby="stats-heading">
        <h2 id="stats-heading" className="sr-only">
          Record counts
        </h2>

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {canStudents && (
            <StatCard
              label="Students"
              value={studentList.data.length}
              loading={studentList.loading}
              icon="students"
              to="/students"
            />
          )}
          {canGuardians && (
            <StatCard
              label="Guardians"
              value={guardianList.data.length}
              loading={guardianList.loading}
              icon="guardian"
              to="/guardians"
            />
          )}
          {canEmployees && (
            <StatCard
              label="Employees"
              value={employeeList.data.length}
              loading={employeeList.loading}
              icon="employee"
              to="/employees"
            />
          )}
          {canClasses && (
            <StatCard
              label="Classes this year"
              value={classList.data.length}
              loading={classList.loading}
              icon="book"
              to="/classes"
            />
          )}
          {canSubjects && (
            <StatCard
              label="Subjects"
              value={subjectList.data.length}
              loading={subjectList.loading}
              icon="award"
              to="/subjects"
            />
          )}
          {canUsers && (
            <StatCard
              label="User accounts"
              value={userList.data.length}
              loading={userList.loading}
              icon="user"
              to="/users"
            />
          )}
        </div>
      </section>

      {canReports && (
        <section className="mt-8" aria-labelledby="reports-heading">
          <h2
            id="reports-heading"
            className="mb-1 text-sm font-semibold text-slate-800 dark:text-slate-200"
          >
            Reports
          </h2>
          <p className="mb-4 text-sm text-slate-500 dark:text-slate-400">
            Class teachers, class sizes and the two subject breakdowns — generated from the
            register and exportable as PDF.
          </p>

          <Link
            to="/reports"
            className="group flex items-center gap-3 rounded-panel bg-white p-4 shadow-panel ring-1 ring-slate-900/5 transition hover:shadow-raised dark:bg-slate-900 dark:ring-white/10"
          >
            <span
              className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-600 dark:bg-brand-950 dark:text-brand-400"
              aria-hidden="true"
            >
              <NavIcon name="chart" className="size-5" />
            </span>
            <span className="min-w-0 flex-1">
              <span className="block text-sm font-semibold text-slate-800 dark:text-slate-100">
                Open reports
              </span>
              <span className="block text-xs text-slate-500 dark:text-slate-400">
                Four reports, any academic year, one click to PDF.
              </span>
            </span>
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              className="size-4 text-slate-300 transition group-hover:translate-x-0.5 group-hover:text-brand-500 dark:text-slate-600"
              aria-hidden="true"
            >
              <path d="M5 12h14M13 6l6 6-6 6" />
            </svg>
          </Link>
        </section>
      )}

      <section className="mt-8" aria-labelledby="planned-heading">
        <h2
          id="planned-heading"
          className="mb-1 text-sm font-semibold text-slate-800 dark:text-slate-200"
        >
          Coming next
        </h2>
        <p className="mb-4 text-sm text-slate-500 dark:text-slate-400">
          Designed in the data model, not yet built.
        </p>

        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {PLANNED.map((item) => (
            <div
              key={item.label}
              className="flex items-center gap-3 rounded-panel border border-dashed border-slate-300 bg-white/50 p-4 dark:border-slate-700 dark:bg-slate-900/40"
            >
              <span
                className="flex size-9 items-center justify-center rounded-lg bg-slate-100 text-slate-400 dark:bg-slate-800 dark:text-slate-500"
                aria-hidden="true"
              >
                <NavIcon name={item.icon} className="size-4.5" />
              </span>

              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-medium text-slate-600 dark:text-slate-300">
                  {item.label}
                </span>
              </span>

              <span className="shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-[0.6875rem] font-semibold uppercase tracking-wide text-slate-400 dark:bg-slate-800 dark:text-slate-500">
                Planned
              </span>
            </div>
          ))}
        </div>
      </section>
    </>
  );
}
