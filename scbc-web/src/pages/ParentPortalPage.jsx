import { useCallback, useEffect, useMemo, useState } from 'react';
import { useToast } from '@/context/ToastContext';
import { parentPortal } from '@/lib/resources';
import { formatDate, toDateInput } from '@/lib/format';

import PageHeader from '@/components/ui/PageHeader';
import Badge from '@/components/ui/Badge';
import EmptyState from '@/components/ui/EmptyState';
import { LoadingPanel } from '@/components/ui/Spinner';
import { NavIcon } from '@/components/layout/navigation';

const money = (value) =>
  value === null || value === undefined
    ? '—'
    : Number(value).toLocaleString(undefined, {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      });

const TABS = [
  { value: 'marks', label: 'Marks' },
  { value: 'attendance', label: 'Attendance' },
  { value: 'payments', label: 'Fees' },
];

/** The first day of the current month, as a date-input value. */
function startOfMonth() {
  const now = new Date();
  return toDateInput(new Date(now.getFullYear(), now.getMonth(), 1));
}

/**
 * What a parent sees: their own children, and nothing else.
 *
 * Every call here is scoped server-side to the guardian on the signed-in
 * account — there is no student id for this page to get wrong, because one it
 * sends is checked against that list rather than trusted. The page shows a
 * child's own marks and their own class rank; it never shows another child's
 * line, which is why it reads from a purpose-built endpoint rather than
 * filtering the class mark sheet in the browser.
 */
export default function ParentPortalPage() {
  const toast = useToast();

  const [children, setChildren] = useState([]);
  const [childId, setChildId] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [tab, setTab] = useState('marks');

  const [terms, setTerms] = useState([]);
  const [attendance, setAttendance] = useState(null);
  const [fees, setFees] = useState(null);
  const [panelLoading, setPanelLoading] = useState(false);

  const [from, setFrom] = useState(startOfMonth);
  const [to, setTo] = useState(() => toDateInput(new Date()));

  useEffect(() => {
    let cancelled = false;

    parentPortal
      .children()
      .then((rows) => {
        if (cancelled) return;
        setChildren(rows);
        setChildId(rows[0]?.studentId ?? null);
      })
      .catch((problem) => {
        if (!cancelled) setError(problem.message ?? 'Your children could not be loaded.');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const loadPanel = useCallback(async () => {
    if (!childId) return;

    setPanelLoading(true);
    try {
      if (tab === 'marks') {
        setTerms(await parentPortal.terms(childId));
      } else if (tab === 'attendance') {
        setAttendance(await parentPortal.attendance(childId, from, to));
      } else {
        setFees(await parentPortal.payments(childId));
      }
    } catch (problem) {
      toast.error(problem.message ?? 'That could not be loaded.');
    } finally {
      setPanelLoading(false);
    }
  }, [childId, tab, from, to, toast]);

  useEffect(() => {
    loadPanel();
  }, [loadPanel]);

  const child = useMemo(
    () => children.find((row) => row.studentId === childId),
    [children, childId],
  );

  if (loading) return <LoadingPanel label="Loading" />;

  if (error) {
    return (
      <>
        <PageHeader
          eyebrow="Parent"
          title="My children"
          icon={<NavIcon name="home" className="size-5" />}
        />
        <EmptyState title="Not available" message={error} />
      </>
    );
  }

  if (children.length === 0) {
    return (
      <>
        <PageHeader
          eyebrow="Parent"
          title="My children"
          icon={<NavIcon name="home" className="size-5" />}
        />
        <EmptyState
          title="No children are linked to this account"
          message="The school links a parent account to the guardian record its children are registered under. Contact the office if this looks wrong."
        />
      </>
    );
  }

  return (
    <>
      <PageHeader
        eyebrow="Parent"
        title="My children"
        description="Marks, attendance and fees for the children registered under your name."
        icon={<NavIcon name="home" className="size-5" />}
      />

      {/* ---- Which child ---------------------------------------------------- */}
      {children.length > 1 && (
        <div className="mb-5 flex flex-wrap gap-2">
          {children.map((row) => (
            <button
              key={row.studentId}
              type="button"
              onClick={() => setChildId(row.studentId)}
              className={[
                'rounded-lg border px-3.5 py-2 text-left text-sm transition',
                row.studentId === childId
                  ? 'border-brand-400 bg-brand-50 text-brand-800 dark:border-brand-700 dark:bg-brand-950/40 dark:text-brand-300'
                  : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:hover:bg-slate-800',
              ].join(' ')}
            >
              <span className="block font-medium">{row.fullname}</span>
              <span className="block text-xs opacity-70">
                {row.className ?? row.gradeName ?? 'Not enrolled'}
              </span>
            </button>
          ))}
        </div>
      )}

      {child && (
        <div className="mb-5 rounded-panel bg-white p-4 shadow-panel ring-1 ring-slate-900/5 dark:bg-slate-900 dark:ring-white/10">
          <h2 className="text-base font-semibold text-slate-800 dark:text-slate-100">
            {child.fullname}
          </h2>
          <p className="text-xs text-slate-500 dark:text-slate-400">
            Admission {child.admissionNo ?? '—'}
            {child.className ? ` · ${child.className}` : ''}
            {child.dateOfBirth ? ` · born ${formatDate(child.dateOfBirth)}` : ''}
          </p>
        </div>
      )}

      {/* ---- Marks / attendance / fees --------------------------------------- */}
      <div
        role="tablist"
        aria-label="What to show"
        className="mb-5 inline-flex rounded-lg bg-slate-100 p-1 dark:bg-slate-800"
      >
        {TABS.map((entry) => (
          <button
            key={entry.value}
            type="button"
            role="tab"
            aria-selected={tab === entry.value}
            onClick={() => setTab(entry.value)}
            className={[
              'rounded-md px-3.5 py-1.5 text-sm font-medium transition',
              tab === entry.value
                ? 'bg-white text-brand-700 shadow-sm dark:bg-slate-900 dark:text-brand-400'
                : 'text-slate-600 hover:text-slate-800 dark:text-slate-400 dark:hover:text-slate-200',
            ].join(' ')}
          >
            {entry.label}
          </button>
        ))}
      </div>

      {panelLoading ? (
        <LoadingPanel label="Loading" />
      ) : tab === 'marks' ? (
        <MarksPanel terms={terms} />
      ) : tab === 'attendance' ? (
        <AttendancePanel
          summary={attendance}
          from={from}
          to={to}
          onFrom={setFrom}
          onTo={setTo}
        />
      ) : (
        <FeesPanel fees={fees} />
      )}
    </>
  );
}

/* -------------------------------------------------------------------------- */

function MarksPanel({ terms }) {
  if (!terms || terms.length === 0) {
    return (
      <EmptyState
        title="No marks yet"
        message="A term appears here once its marks have been entered. Terms with nothing recorded are left out rather than shown as zeroes."
      />
    );
  }

  return (
    <div className="space-y-5">
      {terms.map((term) => (
        <div
          key={`${term.academicYear}-${term.termId}`}
          className="overflow-hidden rounded-panel bg-white shadow-panel ring-1 ring-slate-900/5 dark:bg-slate-900 dark:ring-white/10"
        >
          <div className="flex flex-wrap items-baseline justify-between gap-2 border-b border-slate-200 px-4 py-3 dark:border-slate-700">
            <h3 className="text-sm font-semibold text-slate-800 dark:text-slate-100">
              {term.termName}
              <span className="ml-2 font-normal text-slate-500 dark:text-slate-400">
                {term.academicYear}
              </span>
            </h3>
            <div className="flex flex-wrap gap-2">
              <Badge tone="neutral">Total {term.total}</Badge>
              {term.average !== null && (
                <Badge tone={term.average >= 75 ? 'positive' : 'neutral'}>
                  Average {term.average.toFixed(1)}
                </Badge>
              )}
              {term.rank !== null && (
                <Badge tone="brand">
                  Rank {term.rank} of {term.outOf}
                </Badge>
              )}
            </div>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500 dark:bg-slate-800/60 dark:text-slate-400">
                <tr>
                  <th className="px-4 py-2.5 text-left font-semibold">Subject</th>
                  <th className="px-4 py-2.5 text-center font-semibold">Marks</th>
                  <th className="px-4 py-2.5 text-center font-semibold">Grade</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                {term.subjects.map((subject) => (
                  <tr key={subject.subject}>
                    <td className="px-4 py-2.5 text-slate-700 dark:text-slate-200">
                      {subject.subject}
                      {subject.categoryName && (
                        <span className="ml-2 text-xs text-slate-400">{subject.categoryName}</span>
                      )}
                    </td>
                    <td className="px-4 py-2.5 text-center tabular-nums">
                      {subject.mark === null ? '—' : subject.mark}
                    </td>
                    <td className="px-4 py-2.5 text-center">
                      <span className="text-xs font-semibold text-slate-600 dark:text-slate-300">
                        {subject.grade ?? '—'}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ))}
    </div>
  );
}

function AttendancePanel({ summary, from, to, onFrom, onTo }) {
  const control =
    'h-10 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm text-slate-800 shadow-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100';

  return (
    <>
      <div className="mb-5 flex flex-col gap-3 rounded-panel bg-white p-4 shadow-panel ring-1 ring-slate-900/5 sm:flex-row sm:items-end dark:bg-slate-900 dark:ring-white/10">
        <label className="sm:w-48">
          <span className="mb-1.5 block text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
            From
          </span>
          <input
            type="date"
            value={from}
            max={to}
            onChange={(event) => onFrom(event.target.value)}
            className={control}
          />
        </label>
        <label className="sm:w-48">
          <span className="mb-1.5 block text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
            To
          </span>
          <input
            type="date"
            value={to}
            min={from}
            onChange={(event) => onTo(event.target.value)}
            className={control}
          />
        </label>
      </div>

      {!summary ? null : summary.daysConducted === 0 ? (
        <EmptyState
          title="No register for this period"
          message="School attendance is marked class by class. A period with no register is a period school was not conducted."
        />
      ) : (
        <div className="overflow-hidden rounded-panel bg-white shadow-panel ring-1 ring-slate-900/5 dark:bg-slate-900 dark:ring-white/10">
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-200 px-4 py-3 dark:border-slate-700">
            <p className="text-sm text-slate-600 dark:text-slate-300">
              Present on {summary.daysPresent} of {summary.daysConducted} day(s)
            </p>
            <Badge tone={summary.attendancePercentage >= 80 ? 'positive' : 'notice'}>
              {summary.attendancePercentage}%
            </Badge>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500 dark:bg-slate-800/60 dark:text-slate-400">
                <tr>
                  <th className="px-4 py-2.5 text-left font-semibold">Week</th>
                  <th className="px-4 py-2.5 text-left font-semibold">Dates</th>
                  <th className="px-4 py-2.5 text-center font-semibold">Present</th>
                  <th className="px-4 py-2.5 text-center font-semibold">Absent</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                {summary.weeks
                  .filter((week) => week.conducted > 0)
                  .map((week) => (
                    <tr key={week.number}>
                      <td className="px-4 py-2.5">Week {week.number}</td>
                      <td className="px-4 py-2.5 text-slate-500 dark:text-slate-400">
                        {week.from} to {week.to}
                      </td>
                      <td className="px-4 py-2.5 text-center">{week.present}</td>
                      <td className="px-4 py-2.5 text-center">{week.absent}</td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </>
  );
}

function FeesPanel({ fees }) {
  if (!fees) return null;

  return (
    <div className="overflow-hidden rounded-panel bg-white shadow-panel ring-1 ring-slate-900/5 dark:bg-slate-900 dark:ring-white/10">
      <dl className="grid grid-cols-3 gap-3 border-b border-slate-200 px-4 py-3 text-sm dark:border-slate-700">
        <div>
          <dt className="text-xs text-slate-500 dark:text-slate-400">Fee for the year</dt>
          <dd className="tabular-nums font-medium">
            {fees.annualFee === null ? 'not set' : money(fees.annualFee)}
          </dd>
        </div>
        <div>
          <dt className="text-xs text-slate-500 dark:text-slate-400">Paid</dt>
          <dd className="tabular-nums font-medium">{money(fees.totalPaid)}</dd>
        </div>
        <div>
          <dt className="text-xs text-slate-500 dark:text-slate-400">Balance</dt>
          <dd className="tabular-nums font-medium">
            {fees.balance === null ? '—' : money(fees.balance)}
          </dd>
        </div>
      </dl>

      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500 dark:bg-slate-800/60 dark:text-slate-400">
            <tr>
              <th className="px-4 py-2.5 text-left font-semibold">Receipt</th>
              <th className="px-4 py-2.5 text-left font-semibold">Date</th>
              <th className="px-4 py-2.5 text-left font-semibold">Grade</th>
              <th className="px-4 py-2.5 text-right font-semibold">Amount</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
            {fees.payments.map((payment) => (
              <tr key={payment.id}>
                <td className="px-4 py-2.5">{payment.billNo ?? '—'}</td>
                <td className="px-4 py-2.5 text-slate-500 dark:text-slate-400">
                  {formatDate(payment.paidDate)}
                </td>
                <td className="px-4 py-2.5">{payment.grade?.name ?? '—'}</td>
                <td className="px-4 py-2.5 text-right tabular-nums">
                  {money(payment.amountPaid)}
                </td>
              </tr>
            ))}
            {fees.payments.length === 0 && (
              <tr>
                <td
                  colSpan={4}
                  className="px-4 py-8 text-center text-sm text-slate-500 dark:text-slate-400"
                >
                  No payments have been recorded yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
