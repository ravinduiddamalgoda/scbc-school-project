import { useCallback, useMemo, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useMutation, useResource } from '@/hooks/useResource';
import { useForm } from '@/hooks/useForm';
import {
  academicYears,
  curriculum,
  distributions,
  employees,
  feeStructures,
  gradeHeads,
  holidays,
  lookups,
  subjectCategories,
  terms,
} from '@/lib/resources';
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
import CurriculumPanel from '@/components/CurriculumPanel';
import FeeStructurePanel from '@/components/FeeStructurePanel';
import DistributionItemsPanel from '@/components/DistributionItemsPanel';
import SetupPanel from '@/components/ui/SetupPanel';

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
  const categoryList = useResource(useCallback(() => subjectCategories.list(), []), {
    enabled: can('Subject').select,
  });
  const termList = useResource(useCallback(() => terms.list(yearId || undefined), [yearId]));
  const headList = useResource(useCallback(() => gradeHeads.list(yearId || undefined), [yearId]));
  const holidayList = useResource(useCallback(() => holidays.list(yearId || undefined), [yearId]));
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

  const canEditSubjects = can('Subject');
  const canEditPayments = can('Payment');

  const curriculumList = useResource(useCallback(() => curriculum.list(), []), {
    enabled: canEditSubjects.select,
  });
  const gradeList = useResource(useCallback(() => lookups.grades(), []));
  const subjectList = useResource(useCallback(() => lookups.subjects(), []), {
    enabled: canEditSubjects.select,
  });
  const distributionItemList = useResource(useCallback(() => distributions.items(), []), {
    enabled: can('Student').select,
  });
  const feeList = useResource(
    useCallback(() => feeStructures.list(yearId || undefined), [yearId]),
    { enabled: canEditPayments.select },
  );

  const reloadAll = () => {
    yearList.reload();
    termList.reload();
    headList.reload();
    holidayList.reload();
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
        <HolidaysPanel
          rows={holidayList.data}
          loading={holidayList.loading}
          yearId={yearId}
          privilege={privilege}
          onChanged={() => holidayList.reload()}
        />
        <GradeHeadsPanel
          rows={headList.data}
          loading={headList.loading}
          yearId={yearId}
          teacherOptions={teacherOptions}
          privilege={privilege}
          onChanged={() => headList.reload()}
        />
        {canEditSubjects.select && (
          <SubjectCategoriesPanel
            rows={categoryList.data}
            loading={categoryList.loading}
            privilege={canEditSubjects}
            onChanged={() => categoryList.reload()}
          />
        )}
        {canEditSubjects.select && (
          <CurriculumPanel
            rows={curriculumList.data}
            grades={gradeList.data}
            subjects={subjectList.data}
            loading={curriculumList.loading}
            privilege={canEditSubjects}
            onChanged={() => curriculumList.reload()}
          />
        )}
        {can('Student').select && (
          <DistributionItemsPanel
            items={distributionItemList.data}
            loading={distributionItemList.loading}
            privilege={can('Student')}
            onChanged={() => distributionItemList.reload()}
          />
        )}
        {canEditPayments.select && (
          <FeeStructurePanel
            rows={feeList.data}
            loading={feeList.loading}
            yearId={yearId}
            privilege={canEditPayments}
            onChanged={() => feeList.reload()}
          />
        )}
      </div>
    </>
  );
}

// The panel shell lives in components/ui/SetupPanel so the curriculum and fee
// panels, which are large enough to have files of their own, use the same one.
const Panel = SetupPanel;

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

// ---- Subject categories ---------------------------------------------------

const EMPTY_CATEGORY = { name: '', sortOrder: '', expectedSubjects: '', active: true };

const CATEGORY_SCHEMA = { name: [required('Category name')] };

/**
 * The bands the mark sheet groups its subject columns into.
 *
 * These were a fixed array in the client until now, which meant a school that
 * ran a fourth optional basket had nowhere to say so and its columns landed in
 * whichever band happened to sort first. The order here is the left-to-right
 * order of the bands on the mark sheet and in the subject reports.
 */
function SubjectCategoriesPanel({ rows, loading, privilege, onChanged }) {
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [pendingDelete, setPendingDelete] = useState(null);

  const form = useForm(EMPTY_CATEGORY, CATEGORY_SCHEMA);
  const { run, saving } = useMutation({ onSuccess: onChanged });

  const openCreate = () => {
    setEditing(null);
    form.reset(EMPTY_CATEGORY);
    setOpen(true);
  };

  const openEdit = (category) => {
    setEditing(category);
    form.reset({
      name: category.name ?? '',
      // Position 0 is the first band, not "unset", so this cannot lean on
      // falsiness.
      sortOrder:
        category.sortOrder === null || category.sortOrder === undefined
          ? ''
          : String(category.sortOrder),
      expectedSubjects:
        category.expectedSubjects === null || category.expectedSubjects === undefined
          ? ''
          : String(category.expectedSubjects),
      active: category.active !== false,
    });
    setOpen(true);
  };

  const submit = async (event) => {
    event.preventDefault();
    if (!form.validateAll()) return;

    const payload = {
      name: form.values.name.trim(),
      // Blank means "leave where it is" on edit, and "put it last" on create;
      // the server decides, so the field is omitted rather than sent as 0.
      sortOrder: form.values.sortOrder === '' ? null : Number(form.values.sortOrder),
      expectedSubjects:
        form.values.expectedSubjects === '' ? null : Number(form.values.expectedSubjects),
      active: form.values.active,
    };

    const { ok } = await run(
      () =>
        editing
          ? subjectCategories.update(editing.id, payload)
          : subjectCategories.create(payload),
      { successMessage: editing ? `${payload.name} updated.` : `${payload.name} added.` },
    );
    if (ok) setOpen(false);
  };

  return (
    <Panel
      title="Subject categories"
      description="The column bands on the mark sheet — the compulsory subjects, then each optional basket."
      actions={
        privilege.insert && (
          <Button size="sm" onClick={openCreate}>
            Add category
          </Button>
        )
      }
    >
      {loading ? (
        <LoadingPanel label="Loading categories" />
      ) : rows.length === 0 ? (
        <EmptyState
          title="No categories yet"
          message="Subjects still work without one — they simply print after the grouped ones, in name order."
        />
      ) : (
        <ul className="divide-y divide-slate-100 dark:divide-slate-800">
          {rows.map((category) => (
            <li key={category.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
              <span className="min-w-0 flex-1">
                <span className="block text-sm font-medium text-slate-800 dark:text-slate-100">
                  {category.name}
                  {category.active === false && (
                    <Badge tone="neutral" className="ml-2">
                      Retired
                    </Badge>
                  )}
                </span>
                <span className="block text-xs text-slate-400 dark:text-slate-500">
                  Prints in position {category.sortOrder ?? 0}
                  {category.expectedSubjects
                    ? ` · students take ${category.expectedSubjects}`
                    : ''}
                </span>
              </span>

              {privilege.update && (
                <Button size="sm" variant="secondary" onClick={() => openEdit(category)}>
                  Edit
                </Button>
              )}
              {privilege.delete && (
                <Button size="sm" variant="ghost" onClick={() => setPendingDelete(category)}>
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
        title={editing ? `Edit ${editing.name}` : 'Add category'}
        description="Position fixes the left-to-right order of the bands on the mark sheet."
        size="md"
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)} disabled={saving}>
              Cancel
            </Button>
            <Button type="submit" form="category-form" loading={saving}>
              Save
            </Button>
          </>
        }
      >
        <form id="category-form" onSubmit={submit} noValidate>
          <FormSection title="Details" columns={1}>
            <TextField label="Name" required placeholder="Category 2" {...form.field('name')} />
            <TextField
              label="Position"
              type="number"
              hint="Lowest prints first. Leave blank to put a new band after the existing ones."
              {...form.field('sortOrder')}
            />
            <TextField
              label="Subjects a student takes"
              type="number"
              hint="Optional. Seven for the compulsory band, one for an optional basket. Used to flag a student whose picks do not match."
              {...form.field('expectedSubjects')}
            />
            <Toggle
              label="In use"
              description="A retired band still labels past mark sheets but cannot be assigned to a subject."
              checked={form.values.active}
              onChange={(next) => form.setValue('active', next)}
            />
          </FormSection>
        </form>
      </Drawer>

      <ConfirmDialog
        open={!!pendingDelete}
        title="Delete this category?"
        message={`${pendingDelete?.name ?? ''} will be removed. A category still grouping subjects cannot be deleted — retire it instead so past mark sheets keep their headings.`}
        confirmLabel="Delete category"
        loading={saving}
        onConfirm={async () => {
          const { ok } = await run(() => subjectCategories.remove(pendingDelete.id), {
            successMessage: 'Category deleted.',
          });
          if (ok) setPendingDelete(null);
        }}
        onCancel={() => setPendingDelete(null)}
      />
    </Panel>
  );
}

// ---- Holidays -------------------------------------------------------------

const EMPTY_HOLIDAY = { date: '', name: '', category: 'Public holiday', note: '' };

const HOLIDAY_SCHEMA = { date: [required('Date')], name: [required('Name')] };

const HOLIDAY_CATEGORIES = ['Public holiday', 'Poya day', 'School event', 'Unscheduled closure'];

/**
 * Days school is not conducted.
 *
 * Both attendance reports treat a day as conducted purely because a register
 * exists for it, so recording a holiday is what keeps it out of the
 * denominator. Without one, nothing stops a register being opened on Poya day,
 * and the day then reads as the whole class being absent.
 */
function HolidaysPanel({ rows, loading, yearId, privilege, onChanged }) {
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [pendingDelete, setPendingDelete] = useState(null);

  const form = useForm(EMPTY_HOLIDAY, HOLIDAY_SCHEMA);
  const { run, saving } = useMutation({ onSuccess: onChanged });

  const openCreate = () => {
    setEditing(null);
    form.reset(EMPTY_HOLIDAY);
    setOpen(true);
  };

  const openEdit = (holiday) => {
    setEditing(holiday);
    form.reset({
      date: toDateInput(holiday.date),
      name: holiday.name ?? '',
      category: holiday.category ?? '',
      note: holiday.note ?? '',
    });
    setOpen(true);
  };

  const submit = async (event) => {
    event.preventDefault();
    if (!form.validateAll()) return;

    const payload = {
      date: form.values.date,
      name: form.values.name.trim(),
      category: form.values.category || null,
      note: form.values.note?.trim() || null,
    };
    const year = yearId ? Number(yearId) : undefined;

    const { ok } = await run(
      () => (editing ? holidays.update(editing.id, payload, year) : holidays.create(payload, year)),
      { successMessage: editing ? 'Holiday updated.' : 'Holiday added.' },
    );
    if (ok) setOpen(false);
  };

  return (
    <Panel
      title="Holidays"
      description="Days school is not conducted. A register cannot be opened on one, so it never counts against attendance."
      actions={
        privilege.insert && (
          <Button size="sm" onClick={openCreate}>
            Add holiday
          </Button>
        )
      }
    >
      {loading ? (
        <LoadingPanel label="Loading holidays" />
      ) : rows.length === 0 ? (
        <EmptyState
          title="No holidays recorded"
          message="Add the public holidays and school closures for this year, so attendance percentages are taken over the days school was actually held."
        />
      ) : (
        <ul className="divide-y divide-slate-100 dark:divide-slate-800">
          {rows.map((holiday) => (
            <li key={holiday.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
              <span className="min-w-0 flex-1">
                <span className="block text-sm font-medium text-slate-800 dark:text-slate-100">
                  {holiday.name}
                  {holiday.category && (
                    <Badge tone="neutral" className="ml-2">
                      {holiday.category}
                    </Badge>
                  )}
                </span>
                <span className="block text-xs text-slate-400 dark:text-slate-500">
                  {formatDate(holiday.date)}
                  {holiday.note ? ` · ${holiday.note}` : ''}
                </span>
              </span>

              {privilege.update && (
                <Button size="sm" variant="secondary" onClick={() => openEdit(holiday)}>
                  Edit
                </Button>
              )}
              {privilege.delete && (
                <Button size="sm" variant="ghost" onClick={() => setPendingDelete(holiday)}>
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
        title={editing ? `Edit ${editing.name}` : 'Add holiday'}
        description="A day already carrying attendance cannot be made a holiday - remove those registers first."
        size="md"
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)} disabled={saving}>
              Cancel
            </Button>
            <Button type="submit" form="holiday-form" loading={saving}>
              Save
            </Button>
          </>
        }
      >
        <form id="holiday-form" onSubmit={submit} noValidate>
          <FormSection title="Details" columns={1}>
            <TextField label="Date" type="date" required {...form.field('date')} />
            <TextField label="Name" required placeholder="Vesak Poya" {...form.field('name')} />
            <SelectField
              label="Kind"
              placeholder="Unspecified"
              options={HOLIDAY_CATEGORIES.map((value) => ({ value, label: value }))}
              {...form.field('category')}
            />
            <TextField label="Note" {...form.field('note')} />
          </FormSection>
        </form>
      </Drawer>

      <ConfirmDialog
        open={!!pendingDelete}
        title="Remove this holiday?"
        message={`${pendingDelete?.name ?? ''} will be removed and that date counts as a school day again. Attendance can then be marked for it.`}
        confirmLabel="Remove holiday"
        loading={saving}
        onConfirm={async () => {
          const { ok } = await run(() => holidays.remove(pendingDelete.id), {
            successMessage: 'Holiday removed.',
          });
          if (ok) setPendingDelete(null);
        }}
        onCancel={() => setPendingDelete(null)}
      />
    </Panel>
  );
}
