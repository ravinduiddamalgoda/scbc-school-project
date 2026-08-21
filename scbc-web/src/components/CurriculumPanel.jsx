import { useEffect, useMemo, useState } from 'react';
import { useToast } from '@/context/ToastContext';
import { curriculum } from '@/lib/resources';

import SetupPanel from '@/components/ui/SetupPanel';
import Button from '@/components/ui/Button';
import { LoadingPanel } from '@/components/ui/Spinner';

const BASKETS = [
  { value: 'Core', label: 'Core — every student takes it' },
  { value: 'Cat 1', label: 'Category 1 — one picked from the basket' },
  { value: 'Cat 2', label: 'Category 2' },
  { value: 'Cat 3', label: 'Category 3' },
  { value: 'General', label: 'General — sat by every A/L candidate' },
];

/**
 * Which subjects each grade is taught.
 *
 * This was previously nowhere in the system. The Classes screen offered all
 * twenty-nine subjects for every class from grade 1 to grade 13, and both
 * subject reports counted against whatever happened to be ticked rather than
 * against the curriculum — so the answer to "how many Sinhala teachers does
 * grade 4 need" depended on somebody's ticking, not on the curriculum.
 *
 * Grade 1 takes five subjects and grade 6 takes thirteen. This is where the
 * school says so. Edited a grade at a time and saved as a set, because the
 * decision being recorded is "this is what grade 6 takes", not thirteen
 * separate ones.
 */
export default function CurriculumPanel({
  rows,
  grades,
  subjects,
  loading,
  privilege,
  onChanged,
}) {
  const toast = useToast();

  const [gradeId, setGradeId] = useState('');
  const [draft, setDraft] = useState([]);
  const [saving, setSaving] = useState(false);

  // Default to the first grade, so the panel opens showing something rather
  // than an empty picker the reader has to act on before seeing anything.
  useEffect(() => {
    if (!gradeId && grades.length > 0) setGradeId(String(grades[0].id));
  }, [grades, gradeId]);

  const forGrade = useMemo(
    () => rows.filter((row) => String(row.gradeId) === String(gradeId)),
    [rows, gradeId],
  );

  useEffect(() => {
    setDraft(
      forGrade.map((row) => ({
        subjectId: row.subjectId,
        basket: row.basket ?? 'Core',
        classTeacherTaught: !!row.classTeacherTaught,
      })),
    );
  }, [forGrade]);

  const chosen = useMemo(() => new Map(draft.map((row) => [row.subjectId, row])), [draft]);

  const toggle = (subjectId) =>
    setDraft((current) =>
      current.some((row) => row.subjectId === subjectId)
        ? current.filter((row) => row.subjectId !== subjectId)
        : [...current, { subjectId, basket: 'Core', classTeacherTaught: false }],
    );

  const update = (subjectId, field, value) =>
    setDraft((current) =>
      current.map((row) => (row.subjectId === subjectId ? { ...row, [field]: value } : row)),
    );

  const save = async () => {
    setSaving(true);
    try {
      await curriculum.saveForGrade(
        Number(gradeId),
        // The order the boxes are ticked in becomes the curriculum order, which
        // is what the mark sheet and both subject reports print by.
        draft.map((row, index) => ({ ...row, sortOrder: index + 1 })),
      );
      toast.success('Curriculum saved.');
      onChanged();
    } catch (error) {
      toast.error(error.message ?? 'The curriculum could not be saved.');
    } finally {
      setSaving(false);
    }
  };

  const gradeName = grades.find((grade) => String(grade.id) === String(gradeId))?.name ?? '';

  return (
    <SetupPanel
      title="Curriculum"
      description="Which subjects each grade is taught. The timetable editor and both subject reports read this."
      actions={
        privilege.update && (
          <Button loading={saving} disabled={!gradeId} onClick={save}>
            Save {gradeName}
          </Button>
        )
      }
    >
      {loading ? (
        <LoadingPanel label="Loading the curriculum" />
      ) : (
        <div className="p-4">
          <label className="mb-4 block sm:w-64">
            <span className="mb-1.5 block text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
              Grade
            </span>
            <select
              value={gradeId}
              onChange={(event) => setGradeId(event.target.value)}
              className="h-10 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm text-slate-800 shadow-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
            >
              {grades.map((grade) => (
                <option key={grade.id} value={grade.id}>
                  {grade.name}
                </option>
              ))}
            </select>
          </label>

          <p className="mb-3 text-xs text-slate-500 dark:text-slate-400">
            {draft.length} subject(s) selected for {gradeName}.
          </p>

          <ul className="space-y-2">
            {subjects.map((subject) => {
              const row = chosen.get(subject.id);
              return (
                <li
                  key={subject.id}
                  className={[
                    'rounded-lg border p-3 transition',
                    row
                      ? 'border-brand-300 bg-brand-50/50 dark:border-brand-800 dark:bg-brand-950/30'
                      : 'border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-900',
                  ].join(' ')}
                >
                  <div className="flex items-start gap-3">
                    <input
                      id={`curriculum-${subject.id}`}
                      type="checkbox"
                      checked={!!row}
                      disabled={!privilege.update}
                      onChange={() => toggle(subject.id)}
                      className="mt-1 size-4 shrink-0 rounded accent-brand-600"
                    />
                    <div className="min-w-0 flex-1">
                      <label
                        htmlFor={`curriculum-${subject.id}`}
                        className="block text-sm font-medium text-slate-700 dark:text-slate-200"
                      >
                        {subject.name}
                        {subject.category?.name && (
                          <span className="ml-2 text-xs font-normal text-slate-400">
                            {subject.category.name}
                          </span>
                        )}
                      </label>

                      {row && (
                        <div className="mt-2 flex flex-col gap-2 sm:flex-row sm:items-center">
                          <select
                            value={row.basket}
                            disabled={!privilege.update}
                            onChange={(event) => update(subject.id, 'basket', event.target.value)}
                            className="h-9 rounded-lg border border-slate-300 bg-white px-2 text-xs dark:border-slate-700 dark:bg-slate-900"
                          >
                            {BASKETS.map((basket) => (
                              <option key={basket.value} value={basket.value}>
                                {basket.label}
                              </option>
                            ))}
                          </select>

                          {/*
                            In grades 1 to 5 the class teacher takes Sinhala,
                            Mathematics, Environment Science and Buddhism, so
                            the Subject Wise Teachers report must expect one
                            teacher per class for those and a free count for
                            English, Tamil and IT. This flag says which is
                            which.
                          */}
                          <label className="flex items-center gap-2 text-xs text-slate-600 dark:text-slate-300">
                            <input
                              type="checkbox"
                              checked={row.classTeacherTaught}
                              disabled={!privilege.update}
                              onChange={(event) =>
                                update(subject.id, 'classTeacherTaught', event.target.checked)
                              }
                              className="size-3.5 rounded accent-brand-600"
                            />
                            Taken by the class teacher
                          </label>
                        </div>
                      )}
                    </div>
                  </div>
                </li>
              );
            })}
          </ul>
        </div>
      )}
    </SetupPanel>
  );
}
