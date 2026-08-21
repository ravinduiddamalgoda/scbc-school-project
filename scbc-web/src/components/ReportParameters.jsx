import { useCallback, useMemo, useState } from 'react';
import { useResource } from '@/hooks/useResource';
import { classes, lookups, payments, students } from '@/lib/resources';

import AcademicYearPicker from '@/components/AcademicYearPicker';

const CONTROL =
  'h-10 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm text-slate-800 shadow-sm transition ' +
  'focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30 ' +
  'disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-400 ' +
  'dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:disabled:bg-slate-800';

/**
 * The inputs a report needs, rendered from what the report itself declared.
 *
 * The catalogue says which parameters a report takes; this renders exactly
 * those. Nothing here knows the name of a single report, so a new one on the
 * server arrives with working controls and no client change.
 */
export default function ReportParameters({ parameters = [], value, onChange, years, yearsLoading }) {
  const needs = (name) => parameters.includes(name);

  // Only fetched when a report on screen actually asks for them.
  const classList = useResource(
    useCallback(() => classes.list(value.academicYearId || undefined), [value.academicYearId]),
    { enabled: needs('classroom') },
  );
  const studentList = useResource(useCallback(() => students.list(), []), {
    enabled: needs('student'),
  });

  const classOptions = useMemo(
    () =>
      classList.data.map((item) => ({
        value: item.id,
        label: `${item.grade?.name ?? ''} · ${item.name}`,
      })),
    [classList.data],
  );

  const studentOptions = useMemo(
    () =>
      studentList.data.map((student) => ({
        value: student.id,
        label: `${student.fullname} · ${student.stu_no ?? 'no admission no.'}`,
      })),
    [studentList.data],
  );

  const [admissionQuery, setAdmissionQuery] = useState('');

  /**
   * Selects the student whose admission number was typed.
   *
   * Silent when nothing matches: the dropdown beside it is still there, and an
   * error toast for a half-typed number would fire on every blur.
   */
  const findByAdmissionNo = async () => {
    const term = admissionQuery.trim();
    if (!term) return;

    try {
      const found = await payments.findStudents(term);
      if (found.length === 1) set({ studentId: String(found[0].id) });
    } catch {
      // Left to the dropdown.
    }
  };

  const set = (patch) => onChange({ ...value, ...patch });

  return (
    <div className="flex flex-wrap items-end gap-3">
      {needs('academicYear') && (
        <AcademicYearPicker
          years={years}
          value={value.academicYearId ?? ''}
          loading={yearsLoading}
          // Changing the year invalidates the class: a class belongs to one
          // year, and keeping the old id would report on the wrong one.
          onChange={(next) => set({ academicYearId: next, classroomId: '' })}
        />
      )}

      {needs('classroom') && (
        <label className="flex flex-col">
          <span className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
            Class
          </span>
          <select
            value={value.classroomId ?? ''}
            disabled={classList.loading || classOptions.length === 0}
            onChange={(event) => set({ classroomId: event.target.value })}
            className={`${CONTROL} sm:w-56`}
          >
            <option value="">
              {classList.error
                ? 'Classes could not be loaded'
                : classOptions.length === 0
                  ? 'No classes in this year'
                  : 'Choose a class…'}
            </option>
            {classOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
      )}

      {needs('student') && (
        <>
          {/*
            Admission-number search ahead of the dropdown. Fees Details is asked
            for one student at a time, and a list of nearly three thousand names
            is not how the office identifies them — they have the number off a
            paper file. Leading zeroes are optional.
          */}
          <label className="flex flex-col">
            <span className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
              Admission no.
            </span>
            <input
              type="search"
              value={admissionQuery}
              placeholder="e.g. 3960"
              onChange={(event) => setAdmissionQuery(event.target.value)}
              onKeyDown={(event) => {
                if (event.key !== 'Enter') return;
                event.preventDefault();
                findByAdmissionNo();
              }}
              onBlur={findByAdmissionNo}
              className={`${CONTROL} sm:w-40`}
            />
          </label>

          <label className="flex flex-col">
            <span className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
              Student
            </span>
            <select
              value={value.studentId ?? ''}
              disabled={studentList.loading || studentOptions.length === 0}
              onChange={(event) => set({ studentId: event.target.value })}
              className={`${CONTROL} sm:w-64`}
            >
              <option value="">
                {studentOptions.length === 0 ? 'No students yet' : 'Choose a student…'}
              </option>
              {studentOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
        </>
      )}

      {needs('month') && (
        <label className="flex flex-col">
          <span className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
            Month
          </span>
          <input
            type="month"
            value={value.month ?? ''}
            onChange={(event) => set({ month: event.target.value })}
            className={`${CONTROL} sm:w-44`}
          />
        </label>
      )}
    </div>
  );
}

/** The lookups the picker itself needs, so pages do not each refetch them. */
export function useReportYears() {
  return useResource(useCallback(() => lookups.academicYears(), []));
}
