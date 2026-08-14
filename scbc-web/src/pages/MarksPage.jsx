import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useResource } from '@/hooks/useResource';
import { useToast } from '@/context/ToastContext';
import { classes, lookups, marks as marksApi, terms } from '@/lib/resources';
import { saveBlob } from '@/lib/download';

import PageHeader from '@/components/ui/PageHeader';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import EmptyState from '@/components/ui/EmptyState';
import { LoadingPanel } from '@/components/ui/Spinner';
import { NavIcon } from '@/components/layout/navigation';
import AcademicYearPicker from '@/components/AcademicYearPicker';

/**
 * Subject-wise marks: pick a class and a term, type down the grid, save once.
 *
 * The grid is the screen's whole reason for existing, so it is built for the
 * way marks are actually entered - a teacher working through one subject's
 * paper pile, down the class list, not across a student's row. Typing a mark
 * and pressing Enter moves down the column to the next student; Tab still
 * moves across, so neither habit is punished.
 *
 * Nothing is calculated here. Totals, averages, ranks and grades come back from
 * the server after a save, because a figure the screen worked out itself would
 * be a second implementation of the arithmetic to keep in step with the
 * exports.
 */
export default function MarksPage() {
  const { hasRole } = useAuth();
  const toast = useToast();

  // Marks are entered by teaching staff rather than through the privilege
  // matrix, so the page gates on the same roles the server checks.
  const canEnter = hasRole('Admin', 'Principal', 'Teacher');

  const [yearId, setYearId] = useState('');
  const [classId, setClassId] = useState('');
  const [termId, setTermId] = useState('');

  const yearList = useResource(useCallback(() => lookups.academicYears(), []));
  const classList = useResource(useCallback(() => classes.list(yearId || undefined), [yearId]));
  const termList = useResource(useCallback(() => terms.list(yearId || undefined), [yearId]));

  const [sheet, setSheet] = useState(null);
  const [draft, setDraft] = useState({});
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [exporting, setExporting] = useState(null);

  const cellRefs = useRef({});

  const load = useCallback(async () => {
    if (!classId || !termId) {
      setSheet(null);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const result = await marksApi.sheet(classId, termId);
      setSheet(result);
      setDraft({});
    } catch (caught) {
      setSheet(null);
      setError(caught.message);
    } finally {
      setLoading(false);
    }
  }, [classId, termId]);

  useEffect(() => {
    load();
  }, [load]);

  // A class belongs to one year and a term belongs to one year, so changing the
  // year has to clear both rather than leave a mismatched pair the server would
  // reject on save.
  const handleYearChange = (next) => {
    setYearId(next);
    setClassId('');
    setTermId('');
  };

  /** Draft edits keyed by enrolment id, merged over what the server sent. */
  const valueOf = (cell) => {
    if (cell.studentSubjectId in draft) return draft[cell.studentSubjectId];
    if (cell.absent) return 'AB';
    return cell.marks === null || cell.marks === undefined ? '' : String(cell.marks);
  };

  const setValue = (cell, raw) =>
    setDraft((current) => ({ ...current, [cell.studentSubjectId]: raw }));

  const dirtyCount = Object.keys(draft).length;

  /**
   * "AB" in any casing marks an absence; anything else has to be 0-100. The
   * check runs as the teacher types so a typo is caught in the cell rather
   * than as a rejected save of the whole screen.
   */
  const invalid = useMemo(() => {
    const bad = {};
    Object.entries(draft).forEach(([id, raw]) => {
      const value = String(raw).trim();
      if (value === '' || value.toUpperCase() === 'AB') return;
      const numeric = Number(value);
      if (!Number.isInteger(numeric) || numeric < 0 || numeric > 100) {
        bad[id] = true;
      }
    });
    return bad;
  }, [draft]);

  const invalidCount = Object.keys(invalid).length;

  const handleSave = async () => {
    if (invalidCount > 0) {
      toast.error('Some marks are not a number between 0 and 100, or "AB".');
      return;
    }

    const entries = Object.entries(draft).map(([id, raw]) => {
      const value = String(raw).trim();
      const absent = value.toUpperCase() === 'AB';
      return {
        studentSubjectId: Number(id),
        marks: absent || value === '' ? null : Number(value),
        absent,
      };
    });

    if (entries.length === 0) return;

    setSaving(true);
    try {
      const result = await marksApi.save(Number(classId), Number(termId), entries);
      setSheet(result);
      setDraft({});
      toast.success(`${entries.length} mark${entries.length === 1 ? '' : 's'} saved.`);
    } catch (caught) {
      toast.error(caught.message);
    } finally {
      setSaving(false);
    }
  };

  const handleExport = async (format) => {
    setExporting(format);
    try {
      const { blob, filename } = await (format === 'excel'
        ? marksApi.excel(classId, termId)
        : marksApi.pdf(classId, termId));
      saveBlob(blob, filename ?? `marks.${format === 'excel' ? 'xlsx' : 'pdf'}`);
    } catch (caught) {
      toast.error(caught.message);
    } finally {
      setExporting(null);
    }
  };

  /**
   * Enter and the arrow keys walk the column, because entry goes subject by
   * subject down the class list. Tab is left alone so it still moves across.
   */
  const handleKeyDown = (event, rowIndex, columnIndex) => {
    const step =
      event.key === 'Enter' || event.key === 'ArrowDown'
        ? 1
        : event.key === 'ArrowUp'
          ? -1
          : 0;
    if (step === 0) return;

    event.preventDefault();
    const next = cellRefs.current[`${rowIndex + step}:${columnIndex}`];
    if (next) {
      next.focus();
      next.select();
    }
  };

  const classOptions = classList.data;
  const termOptions = termList.data;

  const highlighted = sheet?.rows.filter((row) => row.highlight).length ?? 0;
  const unsaved = dirtyCount > 0;

  return (
    <>
      <PageHeader
        eyebrow="Academic"
        title="Subject marks"
        description="One class, one term. The same figures feed the mark sheet, the workbook and the PDF."
        icon={<NavIcon name="award" className="size-5" />}
        actions={
          <AcademicYearPicker
            years={yearList.data}
            value={yearId}
            onChange={handleYearChange}
            loading={yearList.loading}
          />
        }
      />

      {/* ---- Class and term ------------------------------------------------ */}
      <div className="mb-5 flex flex-col gap-3 rounded-panel bg-white p-4 shadow-panel ring-1 ring-slate-900/5 sm:flex-row sm:items-end dark:bg-slate-900 dark:ring-white/10">
        <label className="min-w-0 flex-1">
          <span className="mb-1.5 block text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
            Class
          </span>
          <select
            value={classId}
            onChange={(event) => setClassId(event.target.value)}
            disabled={classList.loading || classOptions.length === 0}
            className={SELECT}
          >
            <option value="">
              {classOptions.length === 0 ? 'No classes in this year' : 'Select a class…'}
            </option>
            {classOptions.map((item) => (
              <option key={item.id} value={item.id}>
                {item.grade?.name} · {item.name} ({item.studentCount} on roll)
              </option>
            ))}
          </select>
        </label>

        <label className="sm:w-64">
          <span className="mb-1.5 block text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
            Term
          </span>
          <select
            value={termId}
            onChange={(event) => setTermId(event.target.value)}
            disabled={termList.loading || termOptions.length === 0}
            className={SELECT}
          >
            <option value="">
              {termOptions.length === 0 ? 'No terms in this year' : 'Select a term…'}
            </option>
            {termOptions.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </select>
        </label>
      </div>

      {!classId || !termId ? (
        <EmptyState
          title="Choose a class and a term"
          message="Pick both and the class list appears here with a column for every subject on its timetable."
        />
      ) : loading ? (
        <LoadingPanel label="Loading mark sheet" />
      ) : error ? (
        <EmptyState title="The mark sheet could not be loaded" message={error} />
      ) : !sheet || sheet.rows.length === 0 ? (
        <EmptyState
          title="Nobody on this roll"
          message="No students are enrolled in this class yet. Enrol them from the student register first."
        />
      ) : (
        <>
          {/* ---- Toolbar ---------------------------------------------------- */}
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-panel bg-white p-4 shadow-panel ring-1 ring-slate-900/5 dark:bg-slate-900 dark:ring-white/10">
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <Badge tone="neutral">{sheet.rows.length} students</Badge>
              <Badge tone="neutral">{sheet.subjects.length} subjects</Badge>
              {highlighted > 0 && (
                <Badge tone="positive">
                  {highlighted} averaging {sheet.highlightAverageFrom}+
                </Badge>
              )}
              {unsaved && <Badge tone="notice">{dirtyCount} unsaved</Badge>}
              {invalidCount > 0 && <Badge tone="negative">{invalidCount} invalid</Badge>}
            </div>

            <div className="flex flex-wrap items-center gap-2">
              <Button
                size="sm"
                variant="secondary"
                onClick={() => handleExport('excel')}
                loading={exporting === 'excel'}
                disabled={unsaved}
              >
                Export Excel
              </Button>
              <Button
                size="sm"
                variant="secondary"
                onClick={() => handleExport('pdf')}
                loading={exporting === 'pdf'}
                disabled={unsaved}
              >
                Export PDF
              </Button>
              {canEnter && (
                <Button onClick={handleSave} loading={saving} disabled={!unsaved || invalidCount > 0}>
                  Save marks
                </Button>
              )}
            </div>
          </div>

          {unsaved && (
            <p className="mb-4 rounded-lg bg-notice-50 p-3 text-sm text-notice-600 dark:bg-notice-900/25 dark:text-notice-500">
              Save before exporting — the workbook and the PDF are produced from what is stored, not
              from what is on screen.
            </p>
          )}

          {/* ---- The grid --------------------------------------------------- */}
          <div className="overflow-x-auto rounded-panel bg-white shadow-panel ring-1 ring-slate-900/5 dark:bg-slate-900 dark:ring-white/10">
            <table className="w-full border-collapse text-sm">
              <thead>
                {/* Category bands, merged across the subjects they cover. */}
                <tr className="bg-slate-50 dark:bg-slate-800/60">
                  <th className={`${HEAD} sticky left-0 z-20 bg-slate-50 dark:bg-slate-800`} colSpan={2}>
                    &nbsp;
                  </th>
                  {sheet.categories.map((category) => (
                    <th key={category.name} className={`${HEAD} text-center`} colSpan={category.span}>
                      {category.name}
                    </th>
                  ))}
                  <th className={`${HEAD} text-center`} colSpan={3}>
                    Result
                  </th>
                </tr>
                <tr className="bg-slate-50 dark:bg-slate-800/60">
                  <th className={`${HEAD} sticky left-0 z-20 w-10 bg-slate-50 dark:bg-slate-800`}>#</th>
                  <th
                    className={`${HEAD} sticky left-10 z-20 min-w-52 bg-slate-50 text-left dark:bg-slate-800`}
                  >
                    Student
                  </th>
                  {sheet.subjects.map((subject) => (
                    <th key={subject.classroomSubjectId} className={`${HEAD} min-w-16 text-center`}>
                      <span title={subject.teacher ? `${subject.name} — ${subject.teacher}` : subject.name}>
                        {subject.code}
                      </span>
                    </th>
                  ))}
                  <th className={`${HEAD} text-center`}>Total</th>
                  <th className={`${HEAD} text-center`}>Avg</th>
                  <th className={`${HEAD} text-center`}>Rank</th>
                </tr>
              </thead>

              <tbody>
                {sheet.rows.map((row, rowIndex) => (
                  <tr
                    key={row.registrationId}
                    className={
                      row.highlight
                        ? 'bg-positive-50/60 dark:bg-positive-900/15'
                        : 'odd:bg-white even:bg-slate-50/40 dark:odd:bg-slate-900 dark:even:bg-slate-800/30'
                    }
                  >
                    <td
                      className={`${CELL} sticky left-0 z-10 text-center text-slate-400 ${
                        row.highlight
                          ? 'bg-positive-50 dark:bg-positive-900/25'
                          : 'bg-inherit'
                      }`}
                    >
                      {row.index}
                    </td>
                    <td
                      className={`${CELL} sticky left-10 z-10 whitespace-nowrap font-medium ${
                        row.highlight
                          ? 'bg-positive-50 dark:bg-positive-900/25'
                          : 'bg-inherit'
                      }`}
                    >
                      {row.studentName}
                      {row.admissionNo && (
                        <span className="ml-2 text-xs font-normal text-slate-400">
                          {row.admissionNo}
                        </span>
                      )}
                    </td>

                    {row.cells.map((cell, columnIndex) => {
                      // A subject the student does not take has no enrolment to
                      // write against, so the cell is inert rather than an input
                      // that would fail on save.
                      if (!cell.enrolled) {
                        return (
                          <td
                            key={columnIndex}
                            className={`${CELL} bg-slate-50/80 text-center text-slate-300 dark:bg-slate-800/50 dark:text-slate-600`}
                            title="Not taken"
                          >
                            —
                          </td>
                        );
                      }

                      const key = `${rowIndex}:${columnIndex}`;
                      const bad = invalid[cell.studentSubjectId];
                      const edited = cell.studentSubjectId in draft;

                      return (
                        <td key={columnIndex} className={`${CELL} p-0`}>
                          <input
                            ref={(node) => {
                              cellRefs.current[key] = node;
                            }}
                            value={valueOf(cell)}
                            onChange={(event) => setValue(cell, event.target.value)}
                            onKeyDown={(event) => handleKeyDown(event, rowIndex, columnIndex)}
                            onFocus={(event) => event.target.select()}
                            disabled={!canEnter}
                            inputMode="numeric"
                            aria-label={`${row.studentName} — ${sheet.subjects[columnIndex].name}`}
                            title={cell.grade === '-' ? undefined : `Grade ${cell.grade}`}
                            className={[
                              'h-9 w-full border-0 bg-transparent text-center text-sm tabular-nums',
                              'focus:bg-brand-50 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-brand-500/40',
                              'dark:focus:bg-brand-900/20',
                              bad
                                ? 'bg-negative-50 text-negative-600 dark:bg-negative-900/30'
                                : edited
                                  ? 'bg-notice-50/70 dark:bg-notice-900/20'
                                  : '',
                              cell.absent ? 'font-semibold text-slate-500' : '',
                            ].join(' ')}
                          />
                        </td>
                      );
                    })}

                    <td className={`${CELL} text-center tabular-nums`}>{row.total}</td>
                    <td
                      className={`${CELL} text-center font-semibold tabular-nums ${
                        row.highlight ? 'text-positive-600 dark:text-positive-500' : ''
                      }`}
                    >
                      {row.average === null || row.average === undefined
                        ? '—'
                        : row.average.toFixed(1)}
                    </td>
                    <td className={`${CELL} text-center tabular-nums`}>{row.rank ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <p className="mt-3 text-xs text-slate-500 dark:text-slate-400">
            Type a mark out of 100, or <strong>AB</strong> for an absence. Enter moves down the
            column; Tab moves across. Clearing a cell removes the mark rather than recording a zero.
            An average is taken over the results recorded so far, so it settles as the term is
            entered. Rows averaging {sheet.highlightAverageFrom} or more are shaded here and in both
            exports.
          </p>
        </>
      )}
    </>
  );
}

const SELECT =
  'h-10 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm text-slate-800 shadow-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30 disabled:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100';

const HEAD =
  'border border-slate-200 px-2 py-2 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:border-slate-700 dark:text-slate-400';

const CELL = 'border border-slate-200 px-2 py-1.5 dark:border-slate-700';
