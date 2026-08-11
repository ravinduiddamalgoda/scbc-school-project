import { useCallback, useEffect, useMemo, useState } from 'react';
import { useMutation } from '@/hooks/useResource';
import { classes, enrolments, lookups } from '@/lib/resources';
import { formatDate, toDateInput } from '@/lib/format';

import Drawer from '@/components/ui/Drawer';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import EmptyState from '@/components/ui/EmptyState';
import { LoadingPanel } from '@/components/ui/Spinner';
import { SelectField, TextField } from '@/components/ui/Field';

/**
 * Places a student in a class and records which of that class's subjects they
 * take.
 *
 * Before this existed a student carried a grade and nothing else, so "how many
 * are in Grade 6 B" and "how many take Art" were questions the database could
 * not answer — which is why both count reports had to be kept by hand.
 *
 * The subject list is not a free choice: it is the timetable of the class being
 * joined. Change the class and the list changes with it.
 */
export default function EnrolmentDrawer({ student, canEdit, onClose }) {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState(null); // enrolment being edited, or 'new'

  const [years, setYears] = useState([]);
  const [statuses, setStatuses] = useState([]);

  const reload = useCallback(async () => {
    if (!student) return;
    setLoading(true);
    try {
      setRows(await enrolments.list(student.id));
    } finally {
      setLoading(false);
    }
  }, [student]);

  useEffect(() => {
    if (!student) {
      setRows([]);
      setEditing(null);
      return;
    }

    reload();
    lookups.academicYears().then(setYears).catch(() => setYears([]));
    lookups.registrationStatuses().then(setStatuses).catch(() => setStatuses([]));
  }, [student, reload]);

  const { run, saving } = useMutation({
    onSuccess: async () => {
      await reload();
      setEditing(null);
    },
  });

  const handleRemove = (enrolment) =>
    run(() => enrolments.remove(enrolment.id), { successMessage: 'Enrolment removed.' });

  return (
    <Drawer
      open={!!student}
      onClose={onClose}
      title={`Class enrolment — ${student?.fullname ?? ''}`}
      description={`Admission number ${student?.stu_no ?? '—'}`}
      size="lg"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Close
          </Button>
          {canEdit && !editing && (
            <Button onClick={() => setEditing('new')}>Enrol in a class</Button>
          )}
        </>
      }
    >
      {loading ? (
        <LoadingPanel label="Loading enrolments" />
      ) : editing ? (
        <EnrolmentForm
          student={student}
          enrolment={editing === 'new' ? null : editing}
          years={years}
          statuses={statuses}
          saving={saving}
          onCancel={() => setEditing(null)}
          onSubmit={(payload) =>
            run(
              () =>
                editing === 'new'
                  ? enrolments.create(payload)
                  : enrolments.update(editing.id, payload),
              {
                successMessage:
                  editing === 'new' ? 'Student enrolled.' : 'Enrolment updated.',
              },
            )
          }
        />
      ) : rows.length === 0 ? (
        <EmptyState
          title="Not enrolled in any class"
          message="Until this student is placed in a class they are counted in no class total and no subject total."
        />
      ) : (
        <ul className="space-y-3">
          {rows.map((row) => (
            <li
              key={row.id}
              className="rounded-panel border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-900"
            >
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="font-semibold text-slate-800 dark:text-slate-100">
                    {row.grade?.name} · {row.classroom?.name}
                  </p>
                  <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
                    {row.academicYear?.name} · enrolled {formatDate(row.date)} · ref {row.regNo}
                  </p>
                  <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
                    Class teacher: {row.classTeacher?.name ?? 'Not assigned'}
                  </p>
                </div>

                <div className="flex shrink-0 items-center gap-2">
                  <Badge tone={row.status?.name === 'Active' ? 'positive' : 'neutral'}>
                    {row.status?.name ?? 'No status'}
                  </Badge>
                  {canEdit && (
                    <>
                      <Button size="sm" variant="secondary" onClick={() => setEditing(row)}>
                        Edit
                      </Button>
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => handleRemove(row)}
                        disabled={saving}
                      >
                        Remove
                      </Button>
                    </>
                  )}
                </div>
              </div>

              <div className="mt-3 border-t border-slate-100 pt-3 dark:border-slate-800">
                <p className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-slate-400">
                  Subjects taken ({row.subjects?.length ?? 0})
                </p>
                {row.subjects?.length ? (
                  <div className="flex flex-wrap gap-1.5">
                    {row.subjects.map((subject) => (
                      <Badge key={subject.id} tone="brand">
                        {subject.subject?.name}
                      </Badge>
                    ))}
                  </div>
                ) : (
                  <p className="text-xs text-slate-400 dark:text-slate-500">
                    None chosen yet — this student is in no subject total.
                  </p>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </Drawer>
  );
}

/**
 * The add/edit form.
 *
 * Class list and subject list both reload as the selection above them changes,
 * so it is never possible to submit a subject that belongs to a different
 * class — the server rejects that too, but the form should not offer it.
 */
function EnrolmentForm({ student, enrolment, years, statuses, saving, onCancel, onSubmit }) {
  const [yearId, setYearId] = useState(enrolment?.academicYear?.id ?? '');
  const [classId, setClassId] = useState(enrolment?.classroom?.id ?? '');
  const [statusId, setStatusId] = useState(enrolment?.status?.id ?? '');
  const [date, setDate] = useState(toDateInput(enrolment?.date) || toDateInput(new Date()));

  const [classOptions, setClassOptions] = useState([]);
  const [timetable, setTimetable] = useState([]);
  const [chosen, setChosen] = useState(
    () => new Set((enrolment?.subjects ?? []).map((subject) => subject.id)),
  );
  const [loadingSubjects, setLoadingSubjects] = useState(false);

  useEffect(() => {
    let cancelled = false;
    classes
      .list(yearId || undefined)
      .then((list) => {
        if (!cancelled) setClassOptions(list);
      })
      .catch(() => {
        if (!cancelled) setClassOptions([]);
      });
    return () => {
      cancelled = true;
    };
  }, [yearId]);

  useEffect(() => {
    if (!classId) {
      setTimetable([]);
      return undefined;
    }

    let cancelled = false;
    setLoadingSubjects(true);

    classes
      .subjects(classId)
      .then((lines) => {
        if (!cancelled) setTimetable(lines);
      })
      .catch(() => {
        if (!cancelled) setTimetable([]);
      })
      .finally(() => {
        if (!cancelled) setLoadingSubjects(false);
      });

    return () => {
      cancelled = true;
    };
  }, [classId]);

  // Moving to another class drops choices that belonged to the old one.
  const handleClassChange = (nextId) => {
    setClassId(nextId);
    if (String(nextId) !== String(enrolment?.classroom?.id ?? '')) {
      setChosen(new Set());
    } else {
      setChosen(new Set((enrolment?.subjects ?? []).map((subject) => subject.id)));
    }
  };

  const toggle = (lineId) =>
    setChosen((current) => {
      const next = new Set(current);
      if (next.has(lineId)) next.delete(lineId);
      else next.add(lineId);
      return next;
    });

  const classSelectOptions = useMemo(
    () =>
      classOptions.map((classroom) => ({
        value: classroom.id,
        label: `${classroom.grade?.name ?? ''} · ${classroom.name} (${classroom.studentCount} enrolled)`,
      })),
    [classOptions],
  );

  const handleSubmit = (event) => {
    event.preventDefault();
    onSubmit({
      studentId: student.id,
      classroomId: Number(classId),
      registrationStatusId: statusId ? Number(statusId) : null,
      date: date || null,
      totalFee: null,
      classroomSubjectIds: [...chosen],
    });
  };

  return (
    <form onSubmit={handleSubmit} noValidate>
      <div className="grid gap-4 sm:grid-cols-2">
        <SelectField
          label="Academic year"
          options={years.map((year) => ({ value: year.id, label: year.name }))}
          placeholder="Current year"
          value={yearId}
          onChange={(event) => {
            setYearId(event.target.value);
            handleClassChange('');
          }}
        />
        <SelectField
          label="Class"
          required
          options={classSelectOptions}
          placeholder={classSelectOptions.length ? 'Select a class…' : 'No classes in this year'}
          value={classId}
          onChange={(event) => handleClassChange(event.target.value)}
        />
        <SelectField
          label="Status"
          options={statuses.map((status) => ({ value: status.id, label: status.name }))}
          placeholder="Active"
          hint="Only active enrolments are counted by the reports."
          value={statusId}
          onChange={(event) => setStatusId(event.target.value)}
        />
        <TextField
          label="Enrolment date"
          type="date"
          value={date}
          onChange={(event) => setDate(event.target.value)}
        />
      </div>

      <div className="mt-6">
        <h3 className="mb-1 text-sm font-semibold text-slate-800 dark:text-slate-200">Subjects</h3>
        <p className="mb-3 text-xs text-slate-500 dark:text-slate-400">
          Taken from the timetable of the class selected above.
        </p>

        {!classId ? (
          <p className="rounded-lg bg-slate-50 p-3 text-xs text-slate-500 dark:bg-slate-800 dark:text-slate-400">
            Select a class first.
          </p>
        ) : loadingSubjects ? (
          <LoadingPanel label="Loading subjects" />
        ) : timetable.length === 0 ? (
          <p className="rounded-lg bg-notice-50 p-3 text-xs text-notice-700 dark:bg-notice-900/25 dark:text-notice-500">
            This class has no timetable yet. Add its subjects on the class register first — until
            then the student can be enrolled but will not appear in any subject total.
          </p>
        ) : (
          <ul className="grid gap-2 sm:grid-cols-2">
            {timetable.map((line) => (
              <li key={line.id}>
                <label
                  className={[
                    'flex cursor-pointer items-start gap-2.5 rounded-lg border p-2.5 text-sm transition',
                    chosen.has(line.id)
                      ? 'border-brand-300 bg-brand-50/50 dark:border-brand-800 dark:bg-brand-950/30'
                      : 'border-slate-200 bg-white hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:hover:bg-slate-800',
                  ].join(' ')}
                >
                  <input
                    type="checkbox"
                    checked={chosen.has(line.id)}
                    onChange={() => toggle(line.id)}
                    className="mt-0.5 size-4 shrink-0 rounded accent-brand-600"
                  />
                  <span className="min-w-0">
                    <span className="block font-medium text-slate-700 dark:text-slate-200">
                      {line.subject?.name}
                    </span>
                    <span className="block text-xs text-slate-400 dark:text-slate-500">
                      {line.teacher?.name ?? 'No teacher assigned'}
                    </span>
                  </span>
                </label>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="mt-6 flex justify-end gap-3">
        <Button variant="secondary" onClick={onCancel} disabled={saving}>
          Cancel
        </Button>
        <Button type="submit" loading={saving} disabled={!classId}>
          {enrolment ? 'Save enrolment' : 'Enrol student'}
        </Button>
      </div>
    </form>
  );
}
