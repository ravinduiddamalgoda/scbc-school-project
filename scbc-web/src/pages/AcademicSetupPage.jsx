import { useCallback, useMemo, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useMutation, useResource } from '@/hooks/useResource';
import { useForm } from '@/hooks/useForm';
import { academicYears, employees, gradeHeads, lookups, terms } from '@/lib/resources';
import { formatDate, orDash, toDateInput } from '@/lib/format';
import { required } from '@/lib/validators';

import PageHeader, { FormSection } from '@/components/ui/PageHeader';
import Drawer from '@/components/ui/Drawer';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import EmptyState from '@/components/ui/EmptyState';
import { LoadingPanel } from '@/components/ui/Spinner';
import { SelectField, TextField, Toggle } from '@/components/ui/Field';
import { NavIcon } from '@/components/layout/navigation';
import AcademicYearPicker from '@/components/AcademicYearPicker';

/**
 * The shape of an academic year: the year itself, its terms, and who heads
 * each grade.
 *
 * These three sit together because they are all things you set up once at the
 * start of a year and then leave alone — and because each of them is the
 * missing input to one of the reports. Terms turn the attendance marks into a
 * Week Attendance summary; grade heads fill the column the Grade Heads
 * workbook always had empty.
 */
export default function AcademicSetupPage() {
  const { can } = useAuth();
  const privilege = can('Class');

  const [yearId, setYearId] = useState('');

  const yearList = useResource(useCallback(() => lookups.academicYears(), []));
  const termList = useResource(useCallback(() => terms.list(yearId || undefined), [yearId]));
  const headList = useResource(useCallback(() => gradeHeads.list(yearId || undefined), [yearId]));
  const employeeList = useResource(useCallback(() => employees.list(), []), {
    enabled: can('Employee').select,
  });

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

  const reloadAll = () => {
    yearList.reload();
    termList.reload();
    headList.reload();
  };

  return (
    <>
      <PageHeader
        eyebrow="Academic setup"
        title="Years, terms and grade heads"
        description="Set once at the start of the year. Every report reads them."
        icon={<NavIcon name="settings" className="size-5" />}
        actions={
          <AcademicYearPicker
            years={yearList.data}
            value={yearId}
            onChange={setYearId}
            loading={yearList.loading}
          />
        }
      />

      <div className="space-y-6">
        <YearsPanel
          years={yearList.data}
          loading={yearList.loading}
          privilege={privilege}
          onChanged={reloadAll}
        />
        <TermsPanel
          rows={termList.data}
          loading={termList.loading}
          yearId={yearId}
          privilege={privilege}
          onChanged={() => termList.reload()}
        />
        <GradeHeadsPanel
          rows={headList.data}
          loading={headList.loading}
          yearId={yearId}
          teacherOptions={teacherOptions}
          privilege={privilege}
          onChanged={() => headList.reload()}
        />
      </div>
    </>
  );
}

function Panel({ title, description, actions, children }) {
  return (
    <section className="overflow-hidden rounded-panel bg-white shadow-panel ring-1 ring-slate-900/5 dark:bg-slate-900 dark:ring-white/10">
      <header className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-200 px-4 py-3 dark:border-slate-800">
        <div className="min-w-0">
          <h2 className="text-sm font-semibold text-slate-800 dark:text-slate-200">{title}</h2>
          <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">{description}</p>
        </div>
        {actions}
      </header>
      {children}
    </section>
  );
}

// ---- Academic years -------------------------------------------------------

const EMPTY_YEAR = { name: '', start_date: '', end_date: '', current_year: false };

function YearsPanel({ years, loading, privilege, onChanged }) {
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [pendingDelete, setPendingDelete] = useState(null);

  const form = useForm(EMPTY_YEAR, { name: [required('Year name')] });
  const { run, saving } = useMutation({ onSuccess: onChanged });

  const openCreate = () => {
    setEditing(null);
    form.reset(EMPTY_YEAR);
    setOpen(true);
  };

  const openEdit = (year) => {
    setEditing(year);
    form.reset({
      name: year.name ?? '',
      start_date: toDateInput(year.start_date),
      end_date: toDateInput(year.end_date),
      current_year: !!year.current_year,
    });
    setOpen(true);
  };

  const submit = async (event) => {
    event.preventDefault();
    if (!form.validateAll()) return;

    const payload = {
      name: form.values.name.trim(),
      start_date: form.values.start_date || null,
      end_date: form.values.end_date || null,
      current_year: form.values.current_year,
    };

    const { ok } = await run(
      () => (editing ? academicYears.update(editing.id, payload) : academicYears.create(payload)),
      { successMessage: editing ? 'Academic year updated.' : 'Academic year added.' },
    );
    if (ok) setOpen(false);
  };

  return (
    <Panel
      title="Academic years"
      description="Everything else — classes, enrolments, attendance, reports — hangs off one of these."
      actions={
        privilege.insert && (
          <Button size="sm" onClick={openCreate}>
            Add year
          </Button>
        )
      }
    >
      {loading ? (
        <LoadingPanel label="Loading years" />
      ) : years.length === 0 ? (
        <EmptyState
          title="No academic years yet"
          message="Add one to start setting up classes — nothing else can be created until a year exists."
        />
      ) : (
        <ul className="divide-y divide-slate-100 dark:divide-slate-800">
          {years.map((year) => (
            <li key={year.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
              <span className="min-w-0 flex-1">
                <span className="block text-sm font-medium text-slate-800 dark:text-slate-100">
                  {year.name}
                  {year.current_year && (
                    <Badge tone="brand" className="ml-2">
                      Current
                    </Badge>
                  )}
                </span>
                <span className="block text-xs text-slate-400 dark:text-slate-500">
                  {year.start_date ? formatDate(year.start_date) : 'No start date'} —{' '}
                  {year.end_date ? formatDate(year.end_date) : 'no end date'}
                </span>
              </span>

              {privilege.update && (
                <Button size="sm" variant="secondary" onClick={() => openEdit(year)}>
                  Edit
                </Button>
              )}
              {privilege.delete && (
                <Button size="sm" variant="ghost" onClick={() => setPendingDelete(year)}>
                  Delete
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}

      <Drawer
        open={open}
        onClose={() => setOpen(false)}
        title={editing ? `Edit ${editing.name}` : 'Add academic year'}
        size="md"
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)} disabled={saving}>
              Cancel
            </Button>
            <Button type="submit" form="year-form" loading={saving}>
              Save
            </Button>
          </>
        }
      >
        <form id="year-form" onSubmit={submit} noValidate>
          <FormSection title="Details" columns={1}>
            <TextField label="Name" required placeholder="2026" {...form.field('name')} />
            <TextField label="Starts" type="date" {...form.field('start_date')} />
            <TextField
              label="Ends"
              type="date"
              hint="Used by the attendance summary when no terms have been set up."
              {...form.field('end_date')}
            />
            <Toggle
              label="Current year"
              description="Reports and class lists default to this year when none is picked. Only one year can be current."
              checked={form.values.current_year}
              onChange={(next) => form.setValue('current_year', next)}
            />
          </FormSection>
        </form>
      </Drawer>

      <ConfirmDialog
        open={!!pendingDelete}
        title="Delete this academic year?"
        message={`${pendingDelete?.name ?? ''} will be removed. If it still holds classes the delete is refused — removing it would orphan their reports.`}
        confirmLabel="Delete year"
        loading={saving}
        onConfirm={async () => {
          const { ok } = await run(() => academicYears.remove(pendingDelete.id), {
            successMessage: 'Academic year deleted.',
          });
          if (ok) setPendingDelete(null);
        }}
        onCancel={() => setPendingDelete(null)}
      />
    </Panel>
  );
}

// ---- Terms ----------------------------------------------------------------

const EMPTY_TERM = { name: '', start_date: '', end_date: '' };

const TERM_SCHEMA = {
  name: [required('Term name')],
  start_date: [required('Start date')],
  end_date: [required('End date')],
};

function TermsPanel({ rows, loading, yearId, privilege, onChanged }) {
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [pendingDelete, setPendingDelete] = useState(null);

  const form = useForm(EMPTY_TERM, TERM_SCHEMA);
  const { run, saving } = useMutation({ onSuccess: onChanged });

  const openCreate = () => {
    setEditing(null);
    form.reset(EMPTY_TERM);
    setOpen(true);
  };

  const openEdit = (term) => {
    setEditing(term);
    form.reset({
      name: term.name ?? '',
      start_date: toDateInput(term.start_date),
      end_date: toDateInput(term.end_date),
    });
    setOpen(true);
  };

  const submit = async (event) => {
    event.preventDefault();
    if (!form.validateAll()) return;

    const payload = {
      name: form.values.name.trim(),
      start_date: form.values.start_date,
      end_date: form.values.end_date,
    };
    const year = yearId ? Number(yearId) : undefined;

    const { ok } = await run(
      () => (editing ? terms.update(editing.id, payload, year) : terms.create(payload, year)),
      { successMessage: editing ? 'Term updated.' : 'Term added.' },
    );
    if (ok) setOpen(false);
  };

  return (
    <Panel
      title="Terms"
      description="The Week Attendance report is a per-term breakdown; these dates are what it counts between."
      actions={
        privilege.insert && (
          <Button size="sm" onClick={openCreate}>
            Add term
          </Button>
        )
      }
    >
      {loading ? (
        <LoadingPanel label="Loading terms" />
      ) : rows.length === 0 ? (
        <EmptyState
          title="No terms in this year"
          message="Without terms the attendance summary still works — it just shows the full year as one column instead of three."
        />
      ) : (
        <ul className="divide-y divide-slate-100 dark:divide-slate-800">
          {rows.map((term) => (
            <li key={term.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
              <span className="min-w-0 flex-1">
                <span className="block text-sm font-medium text-slate-800 dark:text-slate-100">
                  {term.name}
                </span>
                <span className="block text-xs text-slate-400 dark:text-slate-500">
                  {formatDate(term.start_date)} — {formatDate(term.end_date)}
                </span>
              </span>

              {privilege.update && (
                <Button size="sm" variant="secondary" onClick={() => openEdit(term)}>
                  Edit
                </Button>
              )}
              {privilege.delete && (
                <Button size="sm" variant="ghost" onClick={() => setPendingDelete(term)}>
                  Delete
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}

      <Drawer
        open={open}
        onClose={() => setOpen(false)}
        title={editing ? `Edit ${editing.name}` : 'Add term'}
        description="Terms may not overlap — an overlap would count the same school day twice."
        size="md"
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)} disabled={saving}>
              Cancel
            </Button>
            <Button type="submit" form="term-form" loading={saving}>
              Save
            </Button>
          </>
        }
      >
        <form id="term-form" onSubmit={submit} noValidate>
          <FormSection title="Details" columns={1}>
            <TextField label="Name" required placeholder="First Term" {...form.field('name')} />
            <TextField label="Starts" type="date" required {...form.field('start_date')} />
            <TextField label="Ends" type="date" required {...form.field('end_date')} />
          </FormSection>
        </form>
      </Drawer>

      <ConfirmDialog
        open={!!pendingDelete}
        title="Delete this term?"
        message={`${pendingDelete?.name ?? ''} will be removed. Attendance marks are dated, not term-linked, so no mark is lost — the report simply drops a column.`}
        confirmLabel="Delete term"
        loading={saving}
        onConfirm={async () => {
          const { ok } = await run(() => terms.remove(pendingDelete.id), {
            successMessage: 'Term deleted.',
          });
          if (ok) setPendingDelete(null);
        }}
        onCancel={() => setPendingDelete(null)}
      />
    </Panel>
  );
}

// ---- Grade heads ----------------------------------------------------------

function GradeHeadsPanel({ rows, loading, yearId, teacherOptions, privilege, onChanged }) {
  const { run, saving } = useMutation({ onSuccess: onChanged });
  const [busyGrade, setBusyGrade] = useState(null);

  const assign = (gradeId, employeeId) => {
    setBusyGrade(gradeId);
    const year = yearId ? Number(yearId) : undefined;

    const action = employeeId
      ? () => gradeHeads.assign(gradeId, Number(employeeId), year)
      : null;

    if (!action) return;
    run(action, { successMessage: 'Grade head saved.' }).finally(() => setBusyGrade(null));
  };

  const named = rows.filter((row) => row.head).length;

  return (
    <Panel
      title="Grade heads"
      description={`The teacher responsible for a whole grade. ${named} of ${rows.length} assigned.`}
    >
      {loading ? (
        <LoadingPanel label="Loading grade heads" />
      ) : rows.length === 0 ? (
        <EmptyState title="No grades yet" message="Seed the grade list before assigning heads." />
      ) : (
        <ul className="divide-y divide-slate-100 dark:divide-slate-800">
          {rows.map((row) => (
            <li key={row.grade.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
              <span className="w-28 shrink-0 text-sm font-medium text-slate-800 dark:text-slate-100">
                {row.grade.name}
              </span>

              <span className="min-w-0 flex-1">
                {privilege.update ? (
                  <SelectField
                    label=""
                    options={teacherOptions}
                    placeholder="Not assigned"
                    value={row.head?.id ?? ''}
                    disabled={saving && busyGrade === row.grade.id}
                    onChange={(event) => assign(row.grade.id, event.target.value)}
                  />
                ) : (
                  <span className="text-sm text-slate-600 dark:text-slate-300">
                    {orDash(row.head?.name)}
                  </span>
                )}
              </span>

              {row.id && privilege.delete && (
                <Button
                  size="sm"
                  variant="ghost"
                  disabled={saving}
                  onClick={() => run(() => gradeHeads.clear(row.id), { successMessage: 'Cleared.' })}
                >
                  Clear
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
    </Panel>
  );
}
