import { useCallback, useEffect, useMemo, useState } from 'react';
import { useToast } from '@/context/ToastContext';
import { attendance, payments } from '@/lib/resources';
import { saveBlob } from '@/lib/download';
import { toDateInput } from '@/lib/format';

import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import EmptyState from '@/components/ui/EmptyState';
import { LoadingPanel } from '@/components/ui/Spinner';
import { TextField } from '@/components/ui/Field';

const CONTROL =
  'h-10 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm text-slate-800 shadow-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100';

const LABEL =
  'mb-1.5 block text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400';

/**
 * The three letters, and what each one is for.
 *
 * The two absence notices are formal notices under Circular No. 53/2023 — the
 * school is required to send them, and required not to send them to a family
 * whose child does not meet the rule. Which of the three are available is
 * decided by the server and arrives on the summary as `availableLetters`; this
 * only reflects it, and the endpoint re-checks the same rule when a letter is
 * asked for.
 */
const LETTERS = [
  {
    value: 'WEEK',
    label: 'Week attendance letter',
    blurb: 'A weekly summary sent to the family of its own accord.',
    tone: 'brand',
  },
  {
    value: 'TWENTY_DAY',
    label: '20 day absence notice',
    blurb: 'Available from 20 continuous school days absent.',
    tone: 'notice',
  },
  {
    value: 'FORTY_DAY',
    label: '40 day absence notice',
    blurb: 'Available from 40 continuous days. Records the student as having left.',
    tone: 'negative',
  },
];

/** The first day of the month a date falls in, as a date-input value. */
function startOfMonth(value) {
  const date = value ? new Date(value) : new Date();
  return toDateInput(new Date(date.getFullYear(), date.getMonth(), 1));
}

/**
 * Attendance seen one student at a time, and the letters it justifies.
 *
 * The register answers "who was in today". This answers "how has this child
 * been attending" — the question the office is actually asked at the counter,
 * and the one the Ministry's absence circular is about. Answering it previously
 * meant opening a month of register pages and counting by eye.
 */
export default function StudentAttendancePanel() {
  const toast = useToast();

  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [searching, setSearching] = useState(false);
  const [student, setStudent] = useState(null);

  const [from, setFrom] = useState(() => startOfMonth());
  const [to, setTo] = useState(() => toDateInput(new Date()));

  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(false);
  const [downloading, setDownloading] = useState(null);

  // The two things the register cannot supply, left blank so the letter prints
  // the sample's dotted lines rather than inventing a meeting nobody called.
  const [meetingDate, setMeetingDate] = useState('');
  const [meetingTime, setMeetingTime] = useState('');

  const search = async (event) => {
    event.preventDefault();
    if (!query.trim()) return;

    setSearching(true);
    try {
      const found = await payments.findStudents(query.trim());
      setResults(found);
      if (found.length === 0) toast.error('No student matches that.');
      // One exact match is almost always the admission number the clerk typed,
      // so it is opened rather than offered as a list of one.
      if (found.length === 1) selectStudent(found[0]);
    } catch (error) {
      toast.error(error.message ?? 'The search failed.');
    } finally {
      setSearching(false);
    }
  };

  const selectStudent = (row) => {
    setStudent(row);
    setResults([]);
  };

  const load = useCallback(async () => {
    if (!student?.id || !from || !to) {
      setSummary(null);
      return;
    }

    setLoading(true);
    try {
      setSummary(await attendance.forStudent(student.id, from, to));
    } catch (error) {
      toast.error(error.message ?? 'The attendance could not be loaded.');
      setSummary(null);
    } finally {
      setLoading(false);
    }
  }, [student, from, to, toast]);

  useEffect(() => {
    load();
  }, [load]);

  const download = async (type) => {
    setDownloading(type);
    try {
      const file = await attendance.letter(
        student.id,
        type,
        from,
        to,
        meetingDate || undefined,
        meetingTime || undefined,
      );
      saveBlob(file.blob, file.filename ?? 'Letter.pdf');
    } catch (error) {
      toast.error(error.message ?? 'The letter could not be produced.');
    } finally {
      setDownloading(null);
    }
  };

  const available = useMemo(() => new Set(summary?.availableLetters ?? []), [summary]);

  const needsMeeting = available.has('TWENTY_DAY') || available.has('FORTY_DAY');

  return (
    <>
      {/* ---- Find the student ---------------------------------------------- */}
      <form
        onSubmit={search}
        className="mb-5 flex flex-col gap-3 rounded-panel bg-white p-4 shadow-panel ring-1 ring-slate-900/5 sm:flex-row sm:items-end dark:bg-slate-900 dark:ring-white/10"
      >
        <label className="min-w-0 flex-1">
          <span className={LABEL}>Student</span>
          <input
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Admission number or name…"
            className={CONTROL}
          />
        </label>

        <label className="sm:w-44">
          <span className={LABEL}>From</span>
          <input
            type="date"
            value={from}
            max={to}
            onChange={(event) => setFrom(event.target.value)}
            className={CONTROL}
          />
        </label>

        <label className="sm:w-44">
          <span className={LABEL}>To</span>
          <input
            type="date"
            value={to}
            min={from}
            onChange={(event) => setTo(event.target.value)}
            className={CONTROL}
          />
        </label>

        <Button type="submit" loading={searching}>
          Find
        </Button>
      </form>

      {results.length > 1 && (
        <ul className="mb-5 divide-y divide-slate-200 overflow-hidden rounded-panel bg-white shadow-panel ring-1 ring-slate-900/5 dark:divide-slate-700 dark:bg-slate-900 dark:ring-white/10">
          {results.map((row) => (
            <li key={row.id}>
              <button
                type="button"
                onClick={() => selectStudent(row)}
                className="flex w-full items-center justify-between gap-3 px-4 py-2.5 text-left text-sm transition hover:bg-slate-50 dark:hover:bg-slate-800"
              >
                <span className="truncate text-slate-700 dark:text-slate-200">{row.fullname}</span>
                <span className="shrink-0 text-xs text-slate-500 dark:text-slate-400">
                  {row.admissionNo} {row.grade ? `· ${row.grade}` : ''}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}

      {!student ? (
        <EmptyState
          title="Find a student"
          message="Search by admission number or name to see how they have been attending, and to produce the week and absence letters."
        />
      ) : loading ? (
        <LoadingPanel label="Loading attendance" />
      ) : !summary ? null : (
        <>
          {/* ---- Summary ---------------------------------------------------- */}
          <div className="mb-5 rounded-panel bg-white p-4 shadow-panel ring-1 ring-slate-900/5 dark:bg-slate-900 dark:ring-white/10">
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <div>
                <h2 className="text-base font-semibold text-slate-800 dark:text-slate-100">
                  {summary.studentName}
                </h2>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  Admission {summary.admissionNo ?? '—'}
                  {summary.className ? ` · ${summary.className}` : ''}
                  {summary.guardianName ? ` · Guardian: ${summary.guardianName}` : ''}
                </p>
              </div>
              <Badge tone={summary.attendancePercentage >= 80 ? 'positive' : 'notice'}>
                {summary.attendancePercentage}% attendance
              </Badge>
            </div>

            <dl className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
              <Stat label="Days conducted" value={summary.daysConducted} />
              <Stat label="Present" value={summary.daysPresent} />
              <Stat label="Absent" value={summary.daysAbsent} />
              <Stat
                label="Absent in a row"
                value={summary.consecutiveAbsentDays}
                tone={summary.consecutiveAbsentDays >= 20 ? 'negative' : 'neutral'}
              />
            </dl>

            {summary.consecutiveAbsentDays > 0 && (
              <p className="mt-3 rounded-lg bg-notice-50 p-3 text-xs text-notice-700 dark:bg-notice-900/25 dark:text-notice-500">
                Absent on the last {summary.consecutiveAbsentDays} school day(s)
                {summary.absentSince ? `, since ${summary.absentSince}` : ''}. Counted in school
                days, not calendar days — a day with no register was a day school was not held.
              </p>
            )}
          </div>

          {/* ---- Week breakdown --------------------------------------------- */}
          <div className="mb-5 overflow-hidden rounded-panel bg-white shadow-panel ring-1 ring-slate-900/5 dark:bg-slate-900 dark:ring-white/10">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500 dark:bg-slate-800/60 dark:text-slate-400">
                  <tr>
                    <th className="px-4 py-2.5 text-left font-semibold">Week</th>
                    <th className="px-4 py-2.5 text-left font-semibold">Dates</th>
                    <th className="px-4 py-2.5 text-center font-semibold">Conducted</th>
                    <th className="px-4 py-2.5 text-center font-semibold">Present</th>
                    <th className="px-4 py-2.5 text-center font-semibold">Absent</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                  {summary.weeks
                    // A week nobody marked is a week school was not conducted;
                    // a row of zeroes for it would read as five absences.
                    .filter((week) => week.conducted > 0)
                    .map((week) => (
                      <tr key={week.number}>
                        <td className="px-4 py-2.5 text-slate-700 dark:text-slate-200">
                          Week {week.number}
                        </td>
                        <td className="px-4 py-2.5 text-slate-500 dark:text-slate-400">
                          {week.from} to {week.to}
                        </td>
                        <td className="px-4 py-2.5 text-center">{week.conducted}</td>
                        <td className="px-4 py-2.5 text-center">{week.present}</td>
                        <td className="px-4 py-2.5 text-center">{week.absent}</td>
                      </tr>
                    ))}
                  {summary.daysConducted === 0 && (
                    <tr>
                      <td
                        colSpan={5}
                        className="px-4 py-6 text-center text-sm text-slate-500 dark:text-slate-400"
                      >
                        No register has been marked for this student in this period.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>

          {/* ---- Letters ----------------------------------------------------- */}
          <div className="rounded-panel bg-white p-4 shadow-panel ring-1 ring-slate-900/5 dark:bg-slate-900 dark:ring-white/10">
            <h3 className="text-sm font-semibold text-slate-800 dark:text-slate-100">Letters</h3>
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
              A notice becomes available when the record justifies it. The server checks the same
              rule again when the letter is produced.
            </p>

            {needsMeeting && (
              <div className="mt-3 grid gap-3 sm:grid-cols-2">
                <TextField
                  label="Meeting date (optional)"
                  type="date"
                  value={meetingDate}
                  onChange={(event) => setMeetingDate(event.target.value)}
                  hint="Left blank, the letter prints the form's dotted line."
                />
                <TextField
                  label="Meeting time (optional)"
                  placeholder="e.g. 2.30"
                  value={meetingTime}
                  onChange={(event) => setMeetingTime(event.target.value)}
                  hint="Printed before “p.m.” as the sample has it."
                />
              </div>
            )}

            <ul className="mt-4 space-y-2">
              {LETTERS.map((letter) => {
                const enabled = available.has(letter.value);
                return (
                  <li
                    key={letter.value}
                    className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 p-3 dark:border-slate-700"
                  >
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-slate-700 dark:text-slate-200">
                        {letter.label}
                      </p>
                      <p className="text-xs text-slate-500 dark:text-slate-400">{letter.blurb}</p>
                    </div>
                    <Button
                      variant={enabled ? 'primary' : 'secondary'}
                      disabled={!enabled}
                      loading={downloading === letter.value}
                      onClick={() => download(letter.value)}
                    >
                      {enabled ? 'Produce' : 'Not applicable'}
                    </Button>
                  </li>
                );
              })}
            </ul>
          </div>
        </>
      )}
    </>
  );
}

function Stat({ label, value, tone = 'neutral' }) {
  return (
    <div className="rounded-lg bg-slate-50 p-3 dark:bg-slate-800/60">
      <dt className="text-xs text-slate-500 dark:text-slate-400">{label}</dt>
      <dd
        className={[
          'mt-0.5 text-xl font-semibold tabular-nums',
          tone === 'negative'
            ? 'text-rose-600 dark:text-rose-400'
            : 'text-slate-800 dark:text-slate-100',
        ].join(' ')}
      >
        {value}
      </dd>
    </div>
  );
}
