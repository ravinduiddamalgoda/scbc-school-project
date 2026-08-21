import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useResource } from '@/hooks/useResource';
import { useToast } from '@/context/ToastContext';
import { classes, distributions, examExports, lookups } from '@/lib/resources';
import { saveBlob } from '@/lib/download';

import PageHeader from '@/components/ui/PageHeader';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import EmptyState from '@/components/ui/EmptyState';
import { LoadingPanel } from '@/components/ui/Spinner';
import { NavIcon } from '@/components/layout/navigation';
import AcademicYearPicker from '@/components/AcademicYearPicker';
import DistributionItemsDrawer from '@/components/DistributionItemsDrawer';

const KINDS = [
  { value: 'UNIFORM', label: 'Uniforms' },
  { value: 'BOOK', label: 'Books' },
];

/**
 * The Ministry sites the office actually works against.
 *
 * Books are requisitioned on the textbook portal and examination candidates are
 * registered on the Department's own site — neither of which this system can
 * do on the school's behalf, and both of which the clerk was otherwise
 * expected to have bookmarked. Linked from the screen the same job is done on,
 * so the two halves of the task sit together.
 */
const EXTERNAL_LINKS = [
  {
    href: 'https://textbooks.moe.gov.lk/',
    label: 'Books upload',
    note: 'Ministry textbook portal — requisitions and returns.',
  },
  {
    href: 'https://onlineexams.gov.lk/eic/index.php/clogin/',
    label: 'Apply for examinations',
    note: 'Department of Examinations — candidate registration.',
  },
];

const EXAMS = [
  { value: 'OL', label: 'G.C.E. O/L — Grade 11' },
  { value: 'GIT', label: 'GIT — Grade 12' },
  { value: 'AL', label: 'G.C.E. A/L — Grade 13' },
  { value: 'GRADE5', label: 'Grade 5 scholarship' },
];

/**
 * Uniform and book distribution, and the examination candidate exports.
 *
 * These sit together because they are the same job — producing a list the
 * office carries somewhere and signs against. The distribution grid records
 * what was handed out; the exam exports produce the Department's own workbooks.
 *
 * The grid deliberately behaves like the marks screen: same shape, same keys,
 * because the same clerk uses both a fortnight apart.
 */
export default function DistributionPage() {
  const { can } = useAuth();
  const privilege = can('Student');
  const toast = useToast();

  const [yearId, setYearId] = useState('');
  const [classId, setClassId] = useState('');
  const [kind, setKind] = useState('UNIFORM');

  const yearList = useResource(useCallback(() => lookups.academicYears(), []));
  const classList = useResource(useCallback(() => classes.list(yearId || undefined), [yearId]));

  const [sheet, setSheet] = useState(null);
  const [draft, setDraft] = useState({});
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [exporting, setExporting] = useState(false);
  const [managingItems, setManagingItems] = useState(false);

  const [exam, setExam] = useState('OL');
  const [check, setCheck] = useState(null);
  const [checking, setChecking] = useState(false);

  const load = useCallback(async () => {
    if (!classId) {
      setSheet(null);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const result = await distributions.sheet(classId, kind);
      setSheet(result);
      setDraft({});
    } catch (caught) {
      setSheet(null);
      setError(caught.message);
    } finally {
      setLoading(false);
    }
  }, [classId, kind]);

  useEffect(() => {
    load();
  }, [load]);

  const key = (row, cell) => `${row.registrationId}:${cell.itemId}`;

  const valueOf = (row, cell) => {
    const id = key(row, cell);
    if (id in draft) return draft[id];
    return cell.quantity === null || cell.quantity === undefined ? '' : String(cell.quantity);
  };

  const dirtyCount = Object.keys(draft).length;
  const unsaved = dirtyCount > 0;

  const invalid = useMemo(
    () =>
      Object.entries(draft).filter(([, raw]) => {
        const value = String(raw).trim();
        if (value === '') return false;
        const numeric = Number(value);
        return !Number.isInteger(numeric) || numeric < 0;
      }).length,
    [draft],
  );

  const handleSave = async () => {
    if (invalid > 0) {
      toast.error('Quantities must be whole numbers of zero or more.');
      return;
    }

    const entries = Object.entries(draft).map(([id, raw]) => {
      const [registrationId, itemId] = id.split(':');
      const value = String(raw).trim();
      return {
        registrationId: Number(registrationId),
        itemId: Number(itemId),
        quantity: value === '' ? null : Number(value),
      };
    });

    setSaving(true);
    try {
      const result = await distributions.save(Number(classId), kind, entries);
      setSheet(result);
      setDraft({});
      toast.success('Distribution saved.');
    } catch (caught) {
      toast.error(caught.message);
    } finally {
      setSaving(false);
    }
  };

  const handleExport = async () => {
    setExporting(true);
    try {
      const { blob, filename } = await distributions.excel(classId, kind);
      saveBlob(blob, filename ?? 'distribution.xlsx');
    } catch (caught) {
      toast.error(caught.message);
    } finally {
      setExporting(false);
    }
  };

  const handleCheck = async () => {
    setChecking(true);
    setCheck(null);
    try {
      setCheck(await examExports.check(exam, yearId || undefined));
    } catch (caught) {
      toast.error(caught.message);
    } finally {
      setChecking(false);
    }
  };

  const handleExamDownload = async () => {
    try {
      const { blob, filename } = await examExports.download(exam, yearId || undefined);
      saveBlob(blob, filename ?? 'candidates.xlsx');
    } catch (caught) {
      toast.error(caught.message);
    }
  };

  const classOptions = classList.data;

  return (
    <>
      <PageHeader
        eyebrow="Office"
        title="Distribution & examination lists"
        description="What was handed out, and the candidate workbooks the Department expects."
        icon={<NavIcon name="book" className="size-5" />}
        actions={
          <AcademicYearPicker
            years={yearList.data}
            value={yearId}
            onChange={(next) => {
              setYearId(next);
              setClassId('');
            }}
            loading={yearList.loading}
          />
        }
      />

      {/* ---- Ministry portals ---------------------------------------------- */}
      <div className="mb-5 grid gap-3 sm:grid-cols-2">
        {EXTERNAL_LINKS.map((link) => (
          <a
            key={link.href}
            href={link.href}
            target="_blank"
            rel="noreferrer noopener"
            className="group flex items-start gap-3 rounded-panel bg-white p-4 shadow-panel ring-1 ring-slate-900/5 transition hover:ring-brand-300 dark:bg-slate-900 dark:ring-white/10 dark:hover:ring-brand-700"
          >
            <span className="mt-0.5 shrink-0 rounded-lg bg-brand-50 p-2 text-brand-600 dark:bg-brand-950/40 dark:text-brand-400">
              <NavIcon name="book" className="size-4" />
            </span>
            <span className="min-w-0">
              <span className="flex items-center gap-1.5 text-sm font-medium text-slate-800 group-hover:text-brand-700 dark:text-slate-100 dark:group-hover:text-brand-400">
                {link.label}
                <svg
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.8"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  className="size-3.5"
                  aria-hidden="true"
                >
                  <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6M15 3h6v6M10 14 21 3" />
                </svg>
                <span className="sr-only">(opens in a new tab)</span>
              </span>
              <span className="mt-0.5 block truncate text-xs text-slate-500 dark:text-slate-400">
                {link.note}
              </span>
            </span>
          </a>
        ))}
      </div>

      {/* ---- Distribution -------------------------------------------------- */}
      <div className="mb-5 flex flex-col gap-3 rounded-panel bg-white p-4 shadow-panel ring-1 ring-slate-900/5 sm:flex-row sm:items-end dark:bg-slate-900 dark:ring-white/10">
        <label className="min-w-0 flex-1">
          <span className={LABEL}>Class</span>
          <select
            value={classId}
            onChange={(event) => setClassId(event.target.value)}
            disabled={classList.loading || classOptions.length === 0}
            className={SELECT}
          >
            <option value="">
              {classList.error
                ? 'Classes could not be loaded'
                : classOptions.length === 0
                  ? 'No classes in this year'
                  : 'Select a class…'}
            </option>
            {classOptions.map((item) => (
              <option key={item.id} value={item.id}>
                {item.grade?.name} · {item.name} ({item.studentCount} on roll)
              </option>
            ))}
          </select>
        </label>

        <label className="sm:w-52">
          <span className={LABEL}>Handing out</span>
          <select value={kind} onChange={(event) => setKind(event.target.value)} className={SELECT}>
            {KINDS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>

        {privilege.update && (
          <Button variant="secondary" onClick={() => setManagingItems(true)}>
            Manage items
          </Button>
        )}
      </div>

      {!classId ? (
        <EmptyState
          title="Choose a class"
          message="Pick a class and its roll appears with a column for each item, ready to record what was collected."
        />
      ) : loading ? (
        <LoadingPanel label="Loading the sheet" />
      ) : error ? (
        <EmptyState
          title="The sheet could not be loaded"
          message={error}
          action={
            privilege.update && (
              <Button onClick={() => setManagingItems(true)}>Set up items</Button>
            )
          }
        />
      ) : !sheet || sheet.rows.length === 0 ? (
        <EmptyState
          title="Nobody on this roll"
          message="No students are enrolled in this class yet."
        />
      ) : (
        <>
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-panel bg-white p-4 shadow-panel ring-1 ring-slate-900/5 dark:bg-slate-900 dark:ring-white/10">
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <Badge tone="neutral">{sheet.rows.length} students</Badge>
              <Badge tone="neutral">{sheet.items.length} items</Badge>
              {unsaved && <Badge tone="notice">{dirtyCount} unsaved</Badge>}
              {invalid > 0 && <Badge tone="negative">{invalid} invalid</Badge>}
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <Button size="sm" variant="secondary" onClick={handleExport} loading={exporting}>
                Export sheet
              </Button>
              {privilege.update && (
                <Button onClick={handleSave} loading={saving} disabled={!unsaved || invalid > 0}>
                  Save
                </Button>
              )}
            </div>
          </div>

          <div className="overflow-x-auto rounded-panel bg-white shadow-panel ring-1 ring-slate-900/5 dark:bg-slate-900 dark:ring-white/10">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="bg-slate-50 dark:bg-slate-800/60">
                  <th className={`${HEAD} w-10`}>#</th>
                  <th className={`${HEAD} min-w-52 text-left`}>Student</th>
                  {sheet.items.map((item) => (
                    <th key={item.id} className={`${HEAD} min-w-20 text-center`} title={item.name}>
                      {item.code}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {sheet.rows.map((row) => (
                  <tr
                    key={row.registrationId}
                    className="odd:bg-white even:bg-slate-50/40 dark:odd:bg-slate-900 dark:even:bg-slate-800/30"
                  >
                    <td className={`${CELL} text-center text-slate-400`}>{row.index}</td>
                    <td className={`${CELL} whitespace-nowrap font-medium`}>
                      {row.studentName}
                      {row.admissionNo && (
                        <span className="ml-2 text-xs font-normal text-slate-400">
                          {row.admissionNo}
                        </span>
                      )}
                    </td>
                    {row.cells.map((cell) => (
                      <td key={cell.itemId} className={`${CELL} p-0`}>
                        <input
                          value={valueOf(row, cell)}
                          onChange={(event) =>
                            setDraft((current) => ({
                              ...current,
                              [key(row, cell)]: event.target.value,
                            }))
                          }
                          onFocus={(event) => event.target.select()}
                          disabled={!privilege.update}
                          inputMode="numeric"
                          aria-label={`${row.studentName} — ${
                            sheet.items.find((item) => item.id === cell.itemId)?.name ?? ''
                          }`}
                          className="h-9 w-full border-0 bg-transparent text-center text-sm tabular-nums focus:bg-brand-50 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-brand-500/40 dark:focus:bg-brand-900/20"
                        />
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <p className="mt-3 text-xs text-slate-500 dark:text-slate-400">
            Enter how many of each item the student collected. A blank cell means nothing has been
            issued yet, which is not the same as issuing none. The exported sheet keeps the
            signature and notes columns blank, to be signed on collection.
          </p>
        </>
      )}

      {/* ---- Examination candidate workbooks -------------------------------- */}
      <section className="mt-8 rounded-panel bg-white p-4 shadow-panel ring-1 ring-slate-900/5 dark:bg-slate-900 dark:ring-white/10">
        <h2 className="text-sm font-semibold text-slate-800 dark:text-slate-200">
          Examination candidate lists
        </h2>
        <p className="mt-0.5 mb-4 text-xs text-slate-500 dark:text-slate-400">
          Candidates are taken from the grade that sits each examination. Check first — the
          Department rejects an upload in bulk without saying which row was wrong.
        </p>

        <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
          <label className="min-w-0 flex-1">
            <span className={LABEL}>Examination</span>
            <select
              value={exam}
              onChange={(event) => {
                setExam(event.target.value);
                setCheck(null);
              }}
              className={SELECT}
            >
              {EXAMS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
          <Button variant="secondary" onClick={handleCheck} loading={checking}>
            Check records
          </Button>
          <Button onClick={handleExamDownload}>Download workbook</Button>
        </div>

        {check && (
          <div className="mt-4">
            <div className="flex flex-wrap items-center gap-2">
              <Badge tone="neutral">{check.candidates} candidates</Badge>
              {check.problems.length === 0 ? (
                <Badge tone="positive">Every record is complete</Badge>
              ) : (
                <Badge tone="negative">{check.problems.length} to fix</Badge>
              )}
              <span className="text-xs text-slate-400">{check.filename}</span>
            </div>

            {check.problems.length > 0 && (
              <ul className="mt-3 max-h-64 space-y-1.5 overflow-y-auto rounded-lg bg-negative-50 p-3 text-sm text-negative-600 dark:bg-negative-900/25 dark:text-negative-500">
                {check.problems.map((problem, index) => (
                  <li key={index}>• {problem}</li>
                ))}
              </ul>
            )}
          </div>
        )}
      </section>

      <DistributionItemsDrawer
        open={managingItems}
        onClose={() => setManagingItems(false)}
        onChanged={load}
      />
    </>
  );
}

const LABEL =
  'mb-1.5 block text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400';

const SELECT =
  'h-10 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm text-slate-800 shadow-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30 disabled:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100';

const HEAD =
  'border border-slate-200 px-2 py-2 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:border-slate-700 dark:text-slate-400';

const CELL = 'border border-slate-200 px-2 py-1.5 dark:border-slate-700';
