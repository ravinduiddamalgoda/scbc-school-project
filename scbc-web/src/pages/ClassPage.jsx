import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useMutation, useResource } from '@/hooks/useResource';
import { useForm } from '@/hooks/useForm';
import { classes, employees, lookups } from '@/lib/resources';
import { orDash } from '@/lib/format';
import { maxLength, required } from '@/lib/validators';

import PageHeader, { FormSection } from '@/components/ui/PageHeader';
import DataTable from '@/components/ui/DataTable';
import Drawer from '@/components/ui/Drawer';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import RowActions from '@/components/ui/RowActions';
import EmptyState from '@/components/ui/EmptyState';
import { LoadingPanel } from '@/components/ui/Spinner';
import { SelectField, TextField } from '@/components/ui/Field';
import { NavIcon } from '@/components/layout/navigation';
import AcademicYearPicker from '@/components/AcademicYearPicker';

const EMPTY_FORM = { name: '', gradeId: '', classTeacherId: '', medium: '' };

const MEDIUMS = ['Sinhala', 'English'];

const SCHEMA = {
  name: [required('Class name'), maxLength(45, 'Class name')],
  gradeId: [required('Grade')],
};

/**
 * Class administration: the roll-call unit every report groups by.
 *
 * A class is (grade, name, academic year). The name is a letter for the lower
 * grades and a stream for A/L - "A" through "G", or "MATHS", "BIO/MATHS",
 * "COMMERCE", "ARTS" - which is exactly how the report spreadsheets label them.
 */
export default function ClassPage() {
  const { can } = useAuth();
  const privilege = can('Class');
  const canReadEmployees = can('Employee').select;

  const [yearId, setYearId] = useState('');

  const yearList = useResource(useCallback(() => lookups.academicYears(), []));
  const gradeList = useResource(useCallback(() => lookups.grades(), []));
  const subjectList = useResource(useCallback(() => lookups.subjects(), []));
  const employeeList = useResource(useCallback(() => employees.list(), []), {
    enabled: canReadEmployees,
  });

  const list = useResource(
    useCallback(() => classes.list(yearId || undefined), [yearId]),
  );

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [pendingDelete, setPendingDelete] = useState(null);
  const [timetableFor, setTimetableFor] = useState(null);

  const form = useForm(EMPTY_FORM, SCHEMA);
  const { run, saving } = useMutation({ onSuccess: () => list.reload() });

  const gradeOptions = useMemo(
    () => gradeList.data.map((grade) => ({ value: grade.id, label: grade.name })),
    [gradeList.data],
  );

  const teacherOptions = useMemo(
    () =>
      employeeList.data.map((employee) => ({
        value: employee.id,
        label: `${employee.fullname}${
          employee.designation_id?.name ? ` · ${employee.designation_id.name}` : ''
        }`,
      })),
    [employeeList.data],
  );

  const openCreate = () => {
    setEditing(null);
    form.reset(EMPTY_FORM);
    setFormOpen(true);
  };

  const openEdit = (classroom) => {
    setEditing(classroom);
    form.reset({
      name: classroom.name ?? '',
      gradeId: classroom.grade?.id ?? '',
      classTeacherId: classroom.classTeacher?.id ?? '',
      medium: classroom.medium ?? '',
    });
    setFormOpen(true);
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!form.validateAll()) return;

    const payload = {
      name: form.values.name.trim(),
      gradeId: Number(form.values.gradeId),
      // Falls back to the year the list is showing, so a class never lands in
      // a different year than the one on screen.
      academicYearId: yearId ? Number(yearId) : (editing?.academicYear?.id ?? null),
      classTeacherId: form.values.classTeacherId ? Number(form.values.classTeacherId) : null,
      medium: form.values.medium || null,
    };

    const { ok } = await run(
      () => (editing ? classes.update(editing.id, payload) : classes.create(payload)),
      { successMessage: editing ? 'Class updated.' : 'Class created.' },
    );

    if (ok) setFormOpen(false);
  };

  const handleDelete = async () => {
    const { ok } = await run(() => classes.remove(pendingDelete.id), {
      successMessage: 'Class deleted.',
    });
    if (ok) setPendingDelete(null);
  };

  const columns = [
    {
      key: 'grade',
      header: 'Grade',
      sortValue: (row) => row.grade?.name ?? '',
      render: (row) => (
        <span className="font-medium text-slate-800 dark:text-slate-100">
          {orDash(row.grade?.name)}
        </span>
      ),
    },
    { key: 'name', header: 'Class', render: (row) => orDash(row.name) },
    {
      key: 'classTeacher',
      header: 'Class teacher',
      sortValue: (row) => row.classTeacher?.name ?? '',
      render: (row) =>
        row.classTeacher?.name ?? (
          <span className="text-slate-400 dark:text-slate-500">Not assigned</span>
        ),
    },
    {
      key: 'medium',
      header: 'Medium',
      render: (row) =>
        row.medium ?? <span className="text-slate-400 dark:text-slate-500">Not set</span>,
    },
    {
      key: 'subjectCount',
      header: 'Subjects',
      align: 'center',
      render: (row) => (
        <Badge tone={row.subjectCount > 0 ? 'brand' : 'neutral'}>{row.subjectCount}</Badge>
      ),
    },
    {
      key: 'studentCount',
      header: 'Students',
      align: 'center',
      render: (row) => <span className="tabular-nums">{row.studentCount}</span>,
    },
  ];

  return (
    <>
      <PageHeader
        eyebrow="Classes"
        title="Class register"
        description="Classes, their class teachers and their timetables — the source of all four reports."
        icon={<NavIcon name="book" className="size-5" />}
        actions={
          <>
            <AcademicYearPicker
              years={yearList.data}
              value={yearId}
              onChange={setYearId}
              loading={yearList.loading}
            />
            {privilege.insert && <Button onClick={openCreate}>Add class</Button>}
          </>
        }
      />

      <DataTable
        columns={columns}
        rows={list.data}
        loading={list.loading}
        searchPlaceholder="Search grade, class or teacher…"
        emptyTitle="No classes in this year"
        emptyMessage="Create the classes for this academic year to start enrolling students."
        actions={(row) => (
          <>
            <button
              type="button"
              onClick={() => setTimetableFor(row)}
              title="Timetable"
              aria-label={`Timetable for ${row.grade?.name ?? ''} ${row.name}`}
              className="rounded-lg p-2 text-slate-400 transition hover:bg-slate-100 hover:text-brand-600 dark:hover:bg-slate-800 dark:hover:text-brand-400"
            >
              <NavIcon name="calendar" className="size-4" />
            </button>
            <RowActions
              onEdit={() => openEdit(row)}
              onDelete={() => setPendingDelete(row)}
              canEdit={privilege.update}
              canDelete={privilege.delete}
            />
          </>
        )}
      />

      {/* ---- Create / edit --------------------------------------------------- */}
      <Drawer
        open={formOpen}
        onClose={() => setFormOpen(false)}
        title={editing ? `Edit ${editing.grade?.name ?? ''} ${editing.name}` : 'Add class'}
        description="One class within a grade, for the academic year currently selected."
        size="md"
        footer={
          <>
            <Button variant="secondary" onClick={() => setFormOpen(false)} disabled={saving}>
              Cancel
            </Button>
            <Button type="submit" form="class-form" loading={saving}>
              {editing ? 'Save changes' : 'Add class'}
            </Button>
          </>
        }
      >
        <form id="class-form" onSubmit={handleSubmit} noValidate>
          <FormSection title="Details" columns={1}>
            <SelectField label="Grade" required options={gradeOptions} {...form.field('gradeId')} />
            <TextField
              label="Class name"
              required
              placeholder="A, B, C… or MATHS, COMMERCE, ARTS"
              hint="Lower grades use a letter; A/L classes use the stream name."
              {...form.field('name')}
            />
            <SelectField
              label="Class teacher"
              options={teacherOptions}
              placeholder={canReadEmployees ? 'Not assigned yet' : 'No access to employees'}
              disabled={!canReadEmployees}
              hint="Shown in the Class Teachers report; can be filled in later."
              {...form.field('classTeacherId')}
            />
            <SelectField
              label="Medium"
              options={MEDIUMS.map((value) => ({ value, label: value }))}
              placeholder="Not set"
              hint="Counted by the Medium wise Student Count report."
              {...form.field('medium')}
            />
          </FormSection>
        </form>
      </Drawer>

      {/* ---- Timetable ------------------------------------------------------- */}
      <TimetableDrawer
        classroom={timetableFor}
        subjects={subjectList.data}
        teacherOptions={teacherOptions}
        canEdit={privilege.update}
        onClose={() => setTimetableFor(null)}
        onSaved={() => list.reload()}
      />

      <ConfirmDialog
        open={!!pendingDelete}
        title="Delete this class?"
        message={`${pendingDelete?.grade?.name ?? ''} ${pendingDelete?.name ?? ''} and its timetable will be removed. If students are still enrolled the delete is refused — move them first.`}
        confirmLabel="Delete class"
        loading={saving}
        onConfirm={handleDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </>
  );
}

/**
 * The timetable editor: which subjects this class is taught, and by whom.
 *
 * It is a checklist rather than a row-by-row editor because that is the shape
 * of the decision - a class takes a set of subjects. Saving replaces the whole
 * set, and unticking a subject takes its student enrolments with it, which the
 * warning below the list says plainly.
 */
function TimetableDrawer({ classroom, subjects, teacherOptions, canEdit, onClose, onSaved }) {
  const [lines, setLines] = useState({});
  const [loading, setLoading] = useState(false);

  const { run, saving } = useMutation({ onSuccess: onSaved });

  useEffect(() => {
    if (!classroom) {
      setLines({});
      return;
    }

    let cancelled = false;
    setLoading(true);

    classes
      .subjects(classroom.id)
      .then((current) => {
        if (cancelled) return;
        setLines(
          Object.fromEntries(
            current.map((line) => [
              line.subject.id,
              { teacherId: line.teacher?.id ?? '', studentCount: line.studentCount },
            ]),
          ),
        );
      })
      .catch(() => {
        if (!cancelled) setLines({});
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [classroom]);

  const toggle = (subjectId) =>
    setLines((current) => {
      if (current[subjectId]) {
        const { [subjectId]: _removed, ...rest } = current;
        return rest;
      }
      return { ...current, [subjectId]: { teacherId: '', studentCount: 0 } };
    });

  const setTeacher = (subjectId, teacherId) =>
    setLines((current) => ({ ...current, [subjectId]: { ...current[subjectId], teacherId } }));

  const handleSave = () =>
    run(
      () =>
        classes.saveSubjects(
          classroom.id,
          Object.entries(lines).map(([subjectId, line]) => ({
            subjectId: Number(subjectId),
            teacherId: line.teacherId ? Number(line.teacherId) : null,
          })),
        ),
      { successMessage: 'Timetable saved.' },
    ).then((result) => {
      if (result.ok) onClose();
    });

  const selectedCount = Object.keys(lines).length;

  return (
    <Drawer
      open={!!classroom}
      onClose={onClose}
      title={`Timetable — ${classroom?.grade?.name ?? ''} ${classroom?.name ?? ''}`}
      description={`${selectedCount} subject(s) selected. The teacher chosen here is what the Subject Wise Teachers report counts.`}
      size="lg"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          {canEdit && (
            <Button onClick={handleSave} loading={saving} disabled={loading}>
              Save timetable
            </Button>
          )}
        </>
      }
    >
      {loading ? (
        <LoadingPanel label="Loading timetable" />
      ) : subjects.length === 0 ? (
        <EmptyState
          title="No subjects to choose from"
          message="Add the curriculum subjects first — a class timetable is built from that list."
        />
      ) : (
        <>
          <ul className="space-y-2">
            {subjects.map((subject) => {
              const line = lines[subject.id];
              const checked = !!line;

              return (
                <li
                  key={subject.id}
                  className={[
                    'rounded-lg border p-3 transition',
                    checked
                      ? 'border-brand-300 bg-brand-50/50 dark:border-brand-800 dark:bg-brand-950/30'
                      : 'border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-900',
                  ].join(' ')}
                >
                  <div className="flex items-start gap-3">
                    <input
                      id={`subject-${subject.id}`}
                      type="checkbox"
                      checked={checked}
                      disabled={!canEdit}
                      onChange={() => toggle(subject.id)}
                      className="mt-1 size-4 shrink-0 rounded accent-brand-600"
                    />

                    <div className="min-w-0 flex-1">
                      <label
                        htmlFor={`subject-${subject.id}`}
                        className="block text-sm font-medium text-slate-700 dark:text-slate-200"
                      >
                        {subject.name}
                        {subject.category && (
                          <span className="ml-2 text-xs font-normal text-slate-400">
                            {subject.category}
                          </span>
                        )}
                      </label>

                      {checked && (
                        <div className="mt-2 flex flex-col gap-2 sm:flex-row sm:items-center">
                          <SelectField
                            label="Teacher"
                            className="min-w-0 flex-1"
                            options={teacherOptions}
                            placeholder="Not assigned yet"
                            disabled={!canEdit}
                            value={line.teacherId}
                            onChange={(event) => setTeacher(subject.id, event.target.value)}
                          />
                          {line.studentCount > 0 && (
                            <span className="shrink-0 text-xs text-slate-500 sm:mt-5 dark:text-slate-400">
                              {line.studentCount} student(s) taking this
                            </span>
                          )}
                        </div>
                      )}
                    </div>
                  </div>
                </li>
              );
            })}
          </ul>

          {canEdit && (
            <p className="mt-4 rounded-lg bg-notice-50 p-3 text-xs text-notice-700 dark:bg-notice-900/25 dark:text-notice-500">
              Removing a subject also removes it from every student in this class who was taking
              it. Subjects left ticked keep their enrolments.
            </p>
          )}
        </>
      )}
    </Drawer>
  );
}
