import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useResource } from '@/hooks/useResource';
import { useToast } from '@/context/ToastContext';
import { lookups, sba } from '@/lib/resources';
import { saveBlob } from '@/lib/download';

import PageHeader from '@/components/ui/PageHeader';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import EmptyState from '@/components/ui/EmptyState';
import { LoadingPanel } from '@/components/ui/Spinner';
import { NavIcon } from '@/components/layout/navigation';

const CONTROL =
  'h-10 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm text-slate-800 shadow-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30 disabled:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100';

const LABEL =
  'mb-1.5 block text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400';

const ORDINAL = { 1: '1st', 2: '2nd', 3: '3rd' };

/**
 * School Based Assessment: the Department's coursework marks.
 *
 * Not the marks module. An SBA mark is coursework submitted upwards, awarded a
 * term at a time across two grades by whoever teaches the subject that year; a
 * term examination result is the school's own. The school asked for them to be
 * kept apart, and they are — separate screen, separate privilege module.
 *
 * The screen is deliberately two halves of one page. Marks are *entered* for
 * one grade and one term, because that is when they are awarded; the sheet
 * *shown* underneath is the merge of all five columns, which is what the
 * workbook prints. Editing the entry column and watching the merged total move
 * is the whole point — it is how a teacher knows their column landed in the
 * right place.
 */
export default function SbaPage() {
  const { can } = useAuth();
  const privilege = can('SBA');
  const toast = useToast();

  const thisYear = new Date().getFullYear();

  const [exam, setExam] = useState('AL');
  const [examYear, setExamYear] = useState(thisYear);
  const [subjectId, setSubjectId] = useState('');
  const [medium, setMedium] = useState('Sinhala');
  const [grade, setGrade] = useState('');
  const [term, setTerm] = useState('');

  const structure = useResource(useCallback(() => sba.structure(), []));
  const subjectList = useResource(useCallback(() => lookups.subjects(), []));
  const mediumList = useResource(useCallback(() => lookups.mediums(), []));

  const [sheet, setSheet] = useState(null);
  const [draft, setDraft] = useState({});
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [exporting, setExporting] = useState(false);

  const examStructure = structure.data?.[exam];

  const gradeOptions = useMemo(() => examStructure?.grades ?? [], [examStructure]);

  const termOptions = useMemo(() => {
    if (!examStructure || !grade) return [];
    return examStructure.terms?.[String(grade)] ?? [];
  }, [examStructure, grade]);

  // Default the entry column to the senior grade's most recent term, which is
  // the one a teacher opening this screen is almost always entering.
  useEffect(() => {
    if (gradeOptions.length === 0) return;
    if (!gradeOptions.includes(Number(grade))) {
      setGrade(gradeOptions[gradeOptions.length - 1]);
    }
  }, [gradeOptions, grade]);

  useEffect(() => {
    if (termOptions.length === 0) return;
    if (!termOptions.includes(Number(term))) {
      setTerm(termOptions[termOptions.length - 1]);
    }
  }, [termOptions, term]);

  const load = useCallback(async () => {
    if (!subjectId) {
      setSheet(null);
      return;
    }

    setLoading(true);
    try {
      const next = await sba.sheet(exam, examYear, Number(subjectId), medium);
      setSheet(next);
      setDraft({});
    } catch (error) {
      toast.error(error.message ?? 'The assessment sheet could not be loaded.');
      setSheet(null);
    } finally {
      setLoading(false);
    }
  }, [exam, examYear, subjectId, medium, toast]);

  useEffect(() => {
    load();
  }, [load]);

  /** Which merged column the entry grid is currently editing. */
  const entryColumnIndex = useMemo(() => {
    if (!sheet) return -1;
    return sheet.columns.findIndex(
      (column) => column.grade === Number(grade) && column.term === Number(term),
    );
  }, [sheet, grade, term]);

  /**
   * What is in a box: the pending edit if there is one, otherwise the stored
   * value.
   *
   * `field` is 'marks' for the term column being entered, or 'groupName' /
   * 'projectMarks' — the two per-candidate columns of the Department's sheet,
   * which belong to the whole assessment rather than to one term.
   */
  const valueFor = (row, field) => {
    const pending = draft[row.studentId]?.[field];
    if (pending !== undefined) return pending;

    if (field === 'groupName') return row.groupName ?? '';
    if (field === 'projectMarks') {
      return row.projectMarks === null || row.projectMarks === undefined
        ? ''
        : String(row.projectMarks);
    }

    if (entryColumnIndex < 0) return '';
    const mark = row.marks[entryColumnIndex];
    return mark === null || mark === undefined ? '' : String(mark);
  };

  const setValue = (studentId, field, value) =>
    setDraft((current) => ({
      ...current,
      [studentId]: { ...current[studentId], [field]: value },
    }));

  const save = async () => {
    // Every touched row sends all three of its editable values, not only the
    // one that changed. The server cannot tell a field that was cleared from
    // one the browser simply left out of the JSON, so the payload is the whole
    // truth for that row and the ambiguity never arises.
    const entries = sheet.rows
      .filter((row) => row.studentId in draft)
      .map((row) => {
        const marks = valueFor(row, 'marks');
        const projectMarks = valueFor(row, 'projectMarks');
        return {
          studentId: row.studentId,
          // An empty box clears the mark rather than storing a zero: a term
          // nobody has assessed is not a candidate who scored nothing.
          marks: marks === '' ? null : Number(marks),
          groupName: valueFor(row, 'groupName'),
          projectMarks: projectMarks === '' ? null : Number(projectMarks),
        };
      });

    if (entries.length === 0) {
      toast.error('Nothing has been changed.');
      return;
    }

    setSaving(true);
    try {
      const next = await sba.save(
        exam,
        examYear,
        Number(subjectId),
        Number(grade),
        Number(term),
        entries,
        medium,
      );
      setSheet(next);
      setDraft({});
      toast.success('Marks saved.');
    } catch (error) {
      toast.error(error.message ?? 'The marks could not be saved.');
    } finally {
      setSaving(false);
    }
  };

  const download = async () => {
    setExporting(true);
    try {
      const file = await sba.excel(exam, examYear, Number(subjectId), medium);
      saveBlob(file.blob, file.filename ?? 'SBA.xlsx');
    } catch (error) {
      toast.error(error.message ?? 'The workbook could not be produced.');
    } finally {
      setExporting(false);
    }
  };

  const dirty = Object.keys(draft).length > 0;

  return (
    <>
      <PageHeader
        eyebrow="Examinations"
        title="School Based Assessment"
        description="Coursework marks for the Department, merged across both grades of the assessment."
        icon={<NavIcon name="clipboard" className="size-5" />}
        actions={
          sheet && (
            <Button variant="secondary" loading={exporting} onClick={download}>
              Download workbook
            </Button>
          )
        }
      />

      {/* ---- What is being assessed ----------------------------------------- */}
      <div className="mb-5 grid gap-3 rounded-panel bg-white p-4 shadow-panel ring-1 ring-slate-900/5 sm:grid-cols-2 lg:grid-cols-4 dark:bg-slate-900 dark:ring-white/10">
        <label>
          <span className={LABEL}>Examination</span>
          <select
            value={exam}
            onChange={(event) => setExam(event.target.value)}
            className={CONTROL}
          >
            <option value="AL">G.C.E. A/L — grades 12 &amp; 13</option>
            <option value="OL">G.C.E. O/L — grades 10 &amp; 11</option>
          </select>
        </label>

        <label>
          <span className={LABEL}>Examination year</span>
          <input
            type="number"
            min="2000"
            max="2100"
            value={examYear}
            onChange={(event) => setExamYear(Number(event.target.value))}
            className={CONTROL}
          />
        </label>

        <label>
          <span className={LABEL}>Subject</span>
          <select
            value={subjectId}
            onChange={(event) => setSubjectId(event.target.value)}
            disabled={subjectList.loading}
            className={CONTROL}
          >
            <option value="">Select a subject…</option>
            {subjectList.data.map((subject) => (
              <option key={subject.id} value={subject.id}>
                {subject.name}
                {subject.examCode ? ` (${subject.examCode})` : ''}
              </option>
            ))}
          </select>
        </label>

        <label>
          <span className={LABEL}>Medium</span>
          <select
            value={medium}
            onChange={(event) => setMedium(event.target.value)}
            className={CONTROL}
          >
            {mediumList.data.map((name) => (
              <option key={name} value={name}>
                {name}
              </option>
            ))}
          </select>
        </label>
      </div>

      {!subjectId ? (
        <EmptyState
          title="Choose a subject"
          message="The candidate list is the students enrolled in the examination grade for that year — grade 13 for the A/L, grade 11 for the O/L. Nobody is typed in by hand."
        />
      ) : loading ? (
        <LoadingPanel label="Loading the assessment sheet" />
      ) : !sheet ? null : (
        <>
          {/* ---- The column being entered ---------------------------------- */}
          <div className="mb-5 flex flex-col gap-3 rounded-panel bg-white p-4 shadow-panel ring-1 ring-slate-900/5 sm:flex-row sm:items-end dark:bg-slate-900 dark:ring-white/10">
            <label className="sm:w-48">
              <span className={LABEL}>Entering for grade</span>
              <select
                value={grade}
                onChange={(event) => setGrade(event.target.value)}
                className={CONTROL}
              >
                {gradeOptions.map((value) => (
                  <option key={value} value={value}>
                    Grade {value}
                  </option>
                ))}
              </select>
            </label>

            <label className="sm:w-48">
              <span className={LABEL}>Term</span>
              <select
                value={term}
                onChange={(event) => setTerm(event.target.value)}
                className={CONTROL}
              >
                {termOptions.map((value) => (
                  <option key={value} value={value}>
                    {ORDINAL[value]} term
                  </option>
                ))}
              </select>
            </label>

            <p className="min-w-0 flex-1 text-xs text-slate-500 dark:text-slate-400">
              Only this column is written when you save. The other four belong to different grades
              and terms — often entered a year earlier by a different teacher — and are left
              untouched.
            </p>

            {privilege.update && (
              <Button loading={saving} disabled={!dirty} onClick={save}>
                Save {ORDINAL[term] ?? ''} term
              </Button>
            )}
          </div>

          {/* ---- The merged sheet ------------------------------------------- */}
          <div className="overflow-hidden rounded-panel bg-white shadow-panel ring-1 ring-slate-900/5 dark:bg-slate-900 dark:ring-white/10">
            <div className="flex flex-wrap items-baseline justify-between gap-2 border-b border-slate-200 px-4 py-3 dark:border-slate-700">
              <div>
                <h2 className="text-sm font-semibold text-slate-800 dark:text-slate-100">
                  {sheet.examLabel} {sheet.examYear} — {sheet.subjectName}
                </h2>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  {sheet.schoolName} · School No. {sheet.schoolNo} · Census No. {sheet.censusNo} ·
                  Zone {sheet.zone}
                </p>
              </div>
              <Badge tone="neutral">{sheet.rows.length} candidate(s)</Badge>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 text-xs text-slate-500 dark:bg-slate-800/60 dark:text-slate-400">
                  <tr>
                    <th rowSpan={2} className="px-3 py-2 text-left font-semibold">
                      #
                    </th>
                    <th rowSpan={2} className="px-3 py-2 text-center font-semibold">
                      Group
                    </th>
                    <th rowSpan={2} className="px-3 py-2 text-center font-semibold">
                      Project
                    </th>
                    <th rowSpan={2} className="px-3 py-2 text-left font-semibold">
                      Name with initials
                    </th>
                    <th rowSpan={2} className="px-3 py-2 text-center font-semibold">
                      Total
                    </th>
                    <th
                      colSpan={sheet.columns.length}
                      className="border-l border-slate-200 px-3 py-2 text-center font-semibold dark:border-slate-700"
                    >
                      Assessment category marks
                    </th>
                  </tr>
                  <tr>
                    {sheet.columns.map((column, index) => (
                      <th
                        key={`${column.grade}-${column.term}`}
                        className={[
                          'px-3 py-2 text-center font-semibold',
                          index === 0 ? 'border-l border-slate-200 dark:border-slate-700' : '',
                          index === entryColumnIndex
                            ? 'bg-brand-50 text-brand-700 dark:bg-brand-950/40 dark:text-brand-400'
                            : '',
                        ].join(' ')}
                      >
                        {column.gradeLabel}
                        <span className="block font-normal">{column.termLabel}</span>
                      </th>
                    ))}
                  </tr>
                </thead>

                <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                  {sheet.rows.map((row) => (
                    <tr key={row.studentId}>
                      <td className="px-3 py-2 text-slate-400">{row.index}</td>
                      <td className="px-2 py-1.5 text-center">
                        {privilege.update ? (
                          <input
                            type="text"
                            maxLength={20}
                            value={valueFor(row, 'groupName')}
                            onChange={(event) =>
                              setValue(row.studentId, 'groupName', event.target.value)
                            }
                            className="h-8 w-16 rounded-md border border-slate-300 bg-white px-2 text-center text-sm dark:border-slate-600 dark:bg-slate-800"
                          />
                        ) : (
                          <span className="text-slate-600 dark:text-slate-300">
                            {row.groupName ?? '—'}
                          </span>
                        )}
                      </td>
                      <td className="px-2 py-1.5 text-center">
                        {privilege.update ? (
                          <input
                            type="number"
                            min="0"
                            max="100"
                            value={valueFor(row, 'projectMarks')}
                            onChange={(event) =>
                              setValue(row.studentId, 'projectMarks', event.target.value)
                            }
                            className="h-8 w-16 rounded-md border border-slate-300 bg-white px-2 text-center text-sm tabular-nums dark:border-slate-600 dark:bg-slate-800"
                          />
                        ) : (
                          <span className="tabular-nums text-slate-600 dark:text-slate-300">
                            {row.projectMarks ?? '—'}
                          </span>
                        )}
                      </td>
                      <td className="px-3 py-2 text-slate-700 dark:text-slate-200">
                        {row.nameWithInitials}
                        <span className="ml-2 text-xs text-slate-400">{row.admissionNo}</span>
                      </td>
                      <td className="px-3 py-2 text-center font-semibold tabular-nums text-slate-800 dark:text-slate-100">
                        {row.total}
                      </td>

                      {sheet.columns.map((column, index) => (
                        <td
                          key={`${column.grade}-${column.term}`}
                          className={[
                            'px-2 py-1.5 text-center',
                            index === 0 ? 'border-l border-slate-200 dark:border-slate-700' : '',
                          ].join(' ')}
                        >
                          {index === entryColumnIndex && privilege.update ? (
                            <input
                              type="number"
                              min="0"
                              max="100"
                              value={valueFor(row, 'marks')}
                              onChange={(event) =>
                                setValue(row.studentId, 'marks', event.target.value)
                              }
                              className="h-8 w-16 rounded-md border border-slate-300 bg-white px-2 text-center text-sm tabular-nums focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30 dark:border-slate-600 dark:bg-slate-800"
                            />
                          ) : (
                            <span className="tabular-nums text-slate-600 dark:text-slate-300">
                              {row.marks[index] === null || row.marks[index] === undefined
                                ? '—'
                                : row.marks[index]}
                            </span>
                          )}
                        </td>
                      ))}
                    </tr>
                  ))}

                  {sheet.rows.length === 0 && (
                    <tr>
                      <td
                        colSpan={5 + sheet.columns.length}
                        className="px-4 py-8 text-center text-sm text-slate-500 dark:text-slate-400"
                      >
                        No students are enrolled in the examination grade for {sheet.examYear}.
                        Enrol them into their class first — the candidate list is derived from
                        that, never typed.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </>
  );
}
