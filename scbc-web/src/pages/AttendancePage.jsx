import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useMutation, useResource } from '@/hooks/useResource';
import { useToast } from '@/context/ToastContext';
import { attendance, classes, holidays, lookups } from '@/lib/resources';
import { toDateInput } from '@/lib/format';

import PageHeader from '@/components/ui/PageHeader';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import EmptyState from '@/components/ui/EmptyState';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import { LoadingPanel } from '@/components/ui/Spinner';
import { NavIcon } from '@/components/layout/navigation';
import AcademicYearPicker from '@/components/AcademicYearPicker';
import StudentAttendancePanel from '@/components/StudentAttendancePanel';

/**
 * Attendance marking: pick a class and a day, go down the roll, save once.
 *
 * The roll comes from the class enrolments, so a student admitted today
 * appears on tomorrow's register with nothing else to do. A day with no
 * register is a day school was not conducted - that is what every attendance
 * report counts - so the page never creates one until it is actually saved.
 */
export default function AttendancePage() {
  const { can } = useAuth();
  const privilege = can('Attendance');
  const toast = useToast();

  /**
   * Which half of the module is on screen.
   *
   * Marking is done by class and by day; looking attendance up is done by
   * student. Both are the same module to the school, so they are two views of
   * one screen rather than two entries in the menu.
   */
  const [view, setView] = useState('register');

  const [yearId, setYearId] = useState('');
  const [classId, setClassId] = useState('');
  const [date, setDate] = useState(() => toDateInput(new Date()));

  const yearList = useResource(useCallback(() => lookups.academicYears(), []));
  const classList = useResource(useCallback(() => classes.list(yearId || undefined), [yearId]));
  const holidayList = useResource(useCallback(() => holidays.list(yearId || undefined), [yearId]));

  const [sheet, setSheet] = useState(null);
  const [marks, setMarks] = useState({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [clearing, setClearing] = useState(false);

  const today = toDateInput(new Date());
  const inFuture = date > today;

  // The server refuses a register on a holiday, because both attendance
  // reports count a day as conducted purely because one exists. Saying so
  // here saves marking a whole class before finding out.
  const holidayOn = holidayList.data.find((holiday) => toDateInput(holiday.date) === date);

  const load = useCallback(async () => {
    if (!classId || !date) {
      setSheet(null);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const result = await attendance.sheet(classId, date);
      setSheet(result);
      setMarks(
        Object.fromEntries(
          result.students
            .filter((student) => student.present !== null && student.present !== undefined)
            .map((student) => [student.studentId, student.present]),
        ),
      );
    } catch (caught) {
      setSheet(null);
      setError(caught.message);
    } finally {
      setLoading(false);
    }
  }, [classId, date]);

  useEffect(() => {
    load();
  }, [load]);

  const { run, saving } = useMutation({ onSuccess: () => load() });

  // Reset the class when the year changes: a class belongs to one year, so
  // keeping the old id would silently mark a register in last year's class.
  const handleYearChange = (next) => {
    setYearId(next);
    setClassId('');
  };

  const setMark = (studentId, present) =>
    setMarks((current) => ({ ...current, [studentId]: present }));

  const markAll = (present) =>
    setMarks(Object.fromEntries((sheet?.students ?? []).map((student) => [student.studentId, present])));

  const counts = useMemo(() => {
    const values = Object.values(marks);
    const present = values.filter(Boolean).length;
    const absent = values.filter((value) => value === false).length;
    const total = sheet?.students.length ?? 0;
    return { present, absent, unmarked: total - present - absent, total };
  }, [marks, sheet]);

  const handleSave = () =>
    run(
      () =>
        attendance.save(
          Number(classId),
          date,
          Object.entries(marks).map(([studentId, present]) => ({
            studentId: Number(studentId),
            present,
          })),
        ),
      { successMessage: `Register saved — ${counts.present} present, ${counts.absent} absent.` },
    );

  const handleClear = async () => {
    try {
      await attendance.remove(sheet.id);
      toast.success('Register removed. This day now counts as no school conducted.');
      setClearing(false);
      await load();
    } catch (caught) {
      toast.error(caught.message);
    }
  };

  const classOptions = classList.data;
  const selectedClass = classOptions.find((item) => String(item.id) === String(classId));

  return (
    <>
      <PageHeader
        eyebrow="Attendance"
        title="Mark attendance"
        description="One class, one day. The register feeds both attendance reports."
        icon={<NavIcon name="calendar" className="size-5" />}
        actions={
          <AcademicYearPicker
            years={yearList.data}
            value={yearId}
            onChange={handleYearChange}
            loading={yearList.loading}
          />
        }
      />

      {/* ---- Register or by-student ---------------------------------------- */}
      <div
        role="tablist"
        aria-label="Attendance view"
        className="mb-5 inline-flex rounded-lg bg-slate-100 p-1 dark:bg-slate-800"
      >
        {[
          { value: 'register', label: 'Daily register' },
          { value: 'student', label: 'By student' },
        ].map((tab) => (
          <button
            key={tab.value}
            type="button"
            role="tab"
            aria-selected={view === tab.value}
            onClick={() => setView(tab.value)}
            className={[
              'rounded-md px-3.5 py-1.5 text-sm font-medium transition',
              view === tab.value
                ? 'bg-white text-brand-700 shadow-sm dark:bg-slate-900 dark:text-brand-400'
                : 'text-slate-600 hover:text-slate-800 dark:text-slate-400 dark:hover:text-slate-200',
            ].join(' ')}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {view === 'student' ? (
        <StudentAttendancePanel />
      ) : (
        <>

      {/* ---- Class and date ------------------------------------------------ */}
      <div className="mb-5 flex flex-col gap-3 rounded-panel bg-white p-4 shadow-panel ring-1 ring-slate-900/5 sm:flex-row sm:items-end dark:bg-slate-900 dark:ring-white/10">
        <label className="min-w-0 flex-1">
          <span className="mb-1.5 block text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
            Class
          </span>
          <select
            value={classId}
            onChange={(event) => setClassId(event.target.value)}
            disabled={classList.loading || classOptions.length === 0}
            className="h-10 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm text-slate-800 shadow-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30 disabled:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
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

        <label className="sm:w-52">
          <span className="mb-1.5 block text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
            Date
          </span>
          <input
            type="date"
            value={date}
            max={today}
            onChange={(event) => setDate(event.target.value)}
            className="h-10 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm text-slate-800 shadow-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
          />
        </label>
      </div>

      {inFuture && (
        <p className="mb-4 rounded-lg bg-notice-50 p-3 text-sm text-notice-600 dark:bg-notice-900/25 dark:text-notice-500">
          Attendance cannot be marked for a date in the future.
        </p>
      )}

      {holidayOn && (
        <p className="mb-4 rounded-lg bg-notice-50 p-3 text-sm text-notice-600 dark:bg-notice-900/25 dark:text-notice-500">
          School was not conducted on this date &mdash; <strong>{holidayOn.name}</strong>. No
          register can be opened, so the day counts against nobody&rsquo;s attendance. Remove the
          holiday under Academic setup if school was in fact held.
        </p>
      )}

      {!classId ? (
        <EmptyState
          title="Choose a class"
          message="Pick the class and the day, and its roll appears here ready to mark."
        />
      ) : loading ? (
        <LoadingPanel label="Loading register" />
      ) : error ? (
        <EmptyState title="The register could not be loaded" message={error} />
      ) : !sheet || sheet.students.length === 0 ? (
        <EmptyState
          title="Nobody on this roll"
          message="No students are enrolled in this class yet. Enrol them from the student register first."
        />
      ) : (
        <>
          {/* ---- Summary and bulk actions --------------------------------- */}
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-panel bg-white p-4 shadow-panel ring-1 ring-slate-900/5 dark:bg-slate-900 dark:ring-white/10">
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <Badge tone="positive">{counts.present} present</Badge>
              <Badge tone="negative">{counts.absent} absent</Badge>
              {counts.unmarked > 0 && <Badge tone="notice">{counts.unmarked} unmarked</Badge>}
              <span className="text-slate-400 dark:text-slate-500">of {counts.total} on roll</span>
              {sheet.marked && (
                <span className="text-xs text-slate-400 dark:text-slate-500">· already saved</span>
              )}
            </div>

            {privilege.update && (
              <div className="flex flex-wrap items-center gap-2">
                <Button size="sm" variant="secondary" onClick={() => markAll(true)}>
                  All present
                </Button>
                <Button size="sm" variant="secondary" onClick={() => markAll(false)}>
                  All absent
                </Button>
                {sheet.marked && privilege.delete && (
                  <Button size="sm" variant="ghost" onClick={() => setClearing(true)}>
                    Remove day
                  </Button>
                )}
                <Button onClick={handleSave} loading={saving} disabled={inFuture || !!holidayOn}>
                  Save register
                </Button>
              </div>
            )}
          </div>

          {/* ---- The roll --------------------------------------------------- */}
          <ul className="space-y-2">
            {sheet.students.map((student, index) => {
              const value = marks[student.studentId];
              return (
                <li
                  key={student.studentId}
                  className="flex flex-wrap items-center gap-3 rounded-panel bg-white p-3 shadow-panel ring-1 ring-slate-900/5 dark:bg-slate-900 dark:ring-white/10"
                >
                  <span className="w-6 shrink-0 text-xs font-medium tabular-nums text-slate-400">
                    {index + 1}
                  </span>

                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium text-slate-800 dark:text-slate-100">
                      {student.name}
                    </span>
                    <span className="block text-xs text-slate-400 dark:text-slate-500">
                      {student.studentNo ?? '—'}
                    </span>
                  </span>

                  <span className="flex shrink-0 gap-1.5" role="radiogroup" aria-label={student.name}>
                    <MarkButton
                      label="Present"
                      selected={value === true}
                      tone="positive"
                      disabled={!privilege.update}
                      onClick={() => setMark(student.studentId, true)}
                    />
                    <MarkButton
                      label="Absent"
                      selected={value === false}
                      tone="negative"
                      disabled={!privilege.update}
                      onClick={() => setMark(student.studentId, false)}
                    />
                  </span>
                </li>
              );
            })}
          </ul>

          {counts.unmarked > 0 && privilege.update && (
            <p className="mt-4 rounded-lg bg-slate-50 p-3 text-xs text-slate-500 dark:bg-slate-800 dark:text-slate-400">
              {counts.unmarked} student(s) have no mark. Saving now leaves them blank in the
              register rather than counting them either way — an unmarked student is not the same
              as an absent one, and the attendance percentage would be wrong if it were.
            </p>
          )}
        </>
      )}

      <ConfirmDialog
        open={clearing}
        title="Remove this day's register?"
        message={`Every mark for ${selectedClass?.grade?.name ?? ''} ${selectedClass?.name ?? ''} on ${date} will be deleted, and the day will count as school not conducted in both attendance reports.`}
        confirmLabel="Remove register"
        onConfirm={handleClear}
        onCancel={() => setClearing(false)}
      />
        </>
      )}
    </>
  );
}

function MarkButton({ label, selected, tone, disabled, onClick }) {
  const selectedTone =
    tone === 'positive'
      ? 'border-positive-500 bg-positive-50 text-positive-700 dark:bg-positive-900/30 dark:text-positive-500'
      : 'border-negative-500 bg-negative-50 text-negative-700 dark:bg-negative-900/30 dark:text-negative-500';

  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      disabled={disabled}
      onClick={onClick}
      className={[
        'rounded-lg border px-3 py-1.5 text-xs font-semibold transition disabled:cursor-not-allowed disabled:opacity-60',
        selected
          ? selectedTone
          : 'border-slate-300 bg-white text-slate-500 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-400 dark:hover:bg-slate-800',
      ].join(' ')}
    >
      {label}
    </button>
  );
}
