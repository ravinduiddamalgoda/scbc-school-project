import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useResource } from '@/hooks/useResource';
import { useToast } from '@/context/ToastContext';
import { reports } from '@/lib/resources';
import { saveBlob, toFileStem } from '@/lib/download';
import { formatDateTime } from '@/lib/format';

import PageHeader from '@/components/ui/PageHeader';
import Button from '@/components/ui/Button';
import EmptyState from '@/components/ui/EmptyState';
import { LoadingPanel } from '@/components/ui/Spinner';
import { NavIcon } from '@/components/layout/navigation';
import ReportParameters, { useReportYears } from '@/components/ReportParameters';

/**
 * Every report the server offers.
 *
 * Reports arrive in one shape — titled sections of headed rows — and declare
 * their own parameters, so one renderer and one set of controls cover all of
 * them. A report added on the server shows up here, with the right inputs,
 * without a line changing in this file.
 */
export default function ReportsPage() {
  const { can } = useAuth();
  const canExport = can('Report').select;
  const toast = useToast();

  const catalogue = useResource(useCallback(() => reports.catalogue(), []));
  const yearList = useReportYears();

  const [activeKey, setActiveKey] = useState(null);
  const [params, setParams] = useState({});
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [exporting, setExporting] = useState(false);

  // Land on the first report as soon as the catalogue arrives.
  useEffect(() => {
    if (!activeKey && catalogue.data.length > 0) {
      setActiveKey(catalogue.data[0].key);
    }
  }, [catalogue.data, activeKey]);

  const active = catalogue.data.find((entry) => entry.key === activeKey);
  const parameters = useMemo(() => active?.parameters ?? [], [active]);

  /** Blank strings must not reach the query string as "classroomId=". */
  const query = useMemo(() => {
    const clean = {};
    for (const [key, value] of Object.entries(params)) {
      if (value !== '' && value !== null && value !== undefined) clean[key] = value;
    }
    return clean;
  }, [params]);

  // A report that needs a class cannot run until one is chosen; asking anyway
  // would only produce the server's "choose a class" error on every keystroke.
  const missing = parameters.filter(
    (name) =>
      (name === 'classroom' && !query.classroomId) || (name === 'student' && !query.studentId),
  );

  useEffect(() => {
    if (!activeKey || missing.length > 0) {
      setReport(null);
      setLoading(false);
      return undefined;
    }

    let cancelled = false;
    setLoading(true);
    setError(null);

    reports
      .run(activeKey, query)
      .then((result) => {
        if (!cancelled) setReport(result);
      })
      .catch((caught) => {
        if (!cancelled) {
          setReport(null);
          setError(caught.message);
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeKey, JSON.stringify(query), missing.length]);

  const handleExport = async () => {
    setExporting(true);
    try {
      const { blob, filename } = await reports.pdf(activeKey, query);
      saveBlob(blob, filename ?? `${toFileStem(report?.title)}.pdf`);
      toast.success('Report exported.');
    } catch (caught) {
      toast.error(caught.message);
    } finally {
      setExporting(false);
    }
  };

  return (
    <>
      <PageHeader
        eyebrow="Reports"
        title="School reports"
        description="Generated from the current register — no figure here is typed in by hand."
        icon={<NavIcon name="chart" className="size-5" />}
        actions={
          <Button onClick={handleExport} loading={exporting} disabled={!report || !canExport}>
            <NavIcon name="book" className="size-4" />
            Export PDF
          </Button>
        }
      />

      {catalogue.loading ? (
        <LoadingPanel label="Loading reports" />
      ) : catalogue.data.length === 0 ? (
        <EmptyState
          title="No reports available"
          message="You do not have permission to run reports, or none are configured."
        />
      ) : (
        <>
          <nav aria-label="Reports" className="mb-4 flex flex-wrap gap-2">
            {catalogue.data.map((entry) => {
              const selected = entry.key === activeKey;
              return (
                <button
                  key={entry.key}
                  type="button"
                  aria-current={selected ? 'page' : undefined}
                  onClick={() => setActiveKey(entry.key)}
                  className={[
                    'rounded-lg px-3.5 py-2 text-sm font-semibold transition',
                    selected
                      ? 'bg-brand-600 text-white shadow-sm'
                      : 'bg-white text-slate-600 ring-1 ring-inset ring-slate-300 hover:bg-slate-50 dark:bg-slate-800 dark:text-slate-300 dark:ring-slate-700 dark:hover:bg-slate-700',
                  ].join(' ')}
                >
                  {entry.title}
                </button>
              );
            })}
          </nav>

          <div className="mb-5 rounded-panel bg-white p-4 shadow-panel ring-1 ring-slate-900/5 dark:bg-slate-900 dark:ring-white/10">
            {active && (
              <p className="mb-3 text-sm text-slate-500 dark:text-slate-400">{active.description}</p>
            )}
            <ReportParameters
              parameters={parameters}
              value={params}
              onChange={setParams}
              years={yearList.data}
              yearsLoading={yearList.loading}
            />
          </div>

          {missing.length > 0 ? (
            <EmptyState
              title="One more choice needed"
              message={`Choose a ${missing.join(' and a ')} above to run this report.`}
            />
          ) : loading ? (
            <LoadingPanel label="Building report" />
          ) : error ? (
            <EmptyState title="The report could not be built" message={error} />
          ) : report ? (
            <ReportView report={report} />
          ) : null}
        </>
      )}
    </>
  );
}

function ReportView({ report }) {
  const hasRows = report.sections?.some((section) => section.rows.length > 0);

  if (!hasRows) {
    return (
      <EmptyState
        title={`Nothing to report for ${report.academicYear}`}
        message="Set up the classes for this year, give them timetables, enrol students and mark attendance — the figures appear as soon as those records exist."
      />
    );
  }

  return (
    <div className="space-y-6">
      <p className="text-xs text-slate-400 dark:text-slate-500">
        {report.title} · academic year {report.academicYear} · generated{' '}
        {formatDateTime(report.generatedAt)}
      </p>

      {report.sections
        .filter((section) => section.rows.length > 0)
        .map((section) => (
          <ReportSection key={section.title} section={section} />
        ))}
    </div>
  );
}

function ReportSection({ section }) {
  return (
    <section className="overflow-hidden rounded-panel bg-white shadow-panel ring-1 ring-slate-900/5 dark:bg-slate-900 dark:ring-white/10">
      <header className="border-b border-slate-200 px-4 py-3 dark:border-slate-800">
        <h2 className="text-sm font-semibold text-slate-800 dark:text-slate-200">
          {section.title}
        </h2>
        {section.subtitle && (
          <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">{section.subtitle}</p>
        )}
      </header>

      {/* Wide matrices scroll inside the panel; the page never does. */}
      <div className="scroll-x">
        <table className="w-full min-w-max border-collapse text-sm">
          <thead>
            <tr className="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-950/50">
              {section.columns.map((column, index) => (
                <th
                  key={`${column.header}-${index}`}
                  scope="col"
                  className={[
                    'px-3 py-2.5 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400',
                    alignClass(column.align),
                  ].join(' ')}
                >
                  {column.header}
                </th>
              ))}
            </tr>
          </thead>

          <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
            {section.rows.map((row, rowIndex) => (
              <tr
                key={rowIndex}
                className="transition hover:bg-slate-50 dark:hover:bg-slate-800/50"
              >
                {section.columns.map((column, index) => (
                  <td
                    key={index}
                    className={[
                      'px-3 py-2 text-slate-700 dark:text-slate-300',
                      alignClass(column.align),
                      column.align === 'center' ? 'tabular-nums' : '',
                    ].join(' ')}
                  >
                    {row[index] === '' ? (
                      <span className="text-slate-300 dark:text-slate-600">·</span>
                    ) : (
                      row[index]
                    )}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>

          {section.footer && (
            <tfoot>
              <tr className="border-t-2 border-slate-200 bg-slate-50 dark:border-slate-700 dark:bg-slate-950/50">
                {section.columns.map((column, index) => (
                  <td
                    key={index}
                    className={[
                      'px-3 py-2.5 text-xs font-bold text-slate-700 dark:text-slate-200',
                      alignClass(column.align),
                      column.align === 'center' ? 'tabular-nums' : '',
                    ].join(' ')}
                  >
                    {section.footer[index] ?? ''}
                  </td>
                ))}
              </tr>
            </tfoot>
          )}
        </table>
      </div>
    </section>
  );
}

function alignClass(align) {
  if (align === 'center') return 'text-center';
  if (align === 'right') return 'text-right';
  return 'text-left';
}
