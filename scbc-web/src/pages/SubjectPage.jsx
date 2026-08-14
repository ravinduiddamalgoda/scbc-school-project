import { useCallback, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useMutation, useResource } from '@/hooks/useResource';
import { useForm } from '@/hooks/useForm';
import { subjectCategories, subjects } from '@/lib/resources';
import { orDash } from '@/lib/format';
import { maxLength, required } from '@/lib/validators';

import PageHeader, { FormSection } from '@/components/ui/PageHeader';
import DataTable from '@/components/ui/DataTable';
import Drawer from '@/components/ui/Drawer';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import RowActions from '@/components/ui/RowActions';
import { SelectField, TextField, Toggle } from '@/components/ui/Field';
import { NavIcon } from '@/components/layout/navigation';

const EMPTY_FORM = { name: '', code: '', categoryId: '', active: true };

const SCHEMA = {
  name: [required('Subject name'), maxLength(60, 'Subject name')],
  code: [maxLength(12, 'Short code')],
};

export default function SubjectPage() {
  const { can } = useAuth();
  const privilege = can('Subject');

  const list = useResource(useCallback(() => subjects.list(), []));
  // The bands are rows now rather than a fixed array, so the school can add one
  // without a release - which is what the mark sheet's column groups follow.
  const categoryList = useResource(useCallback(() => subjectCategories.list(), []));

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [pendingDelete, setPendingDelete] = useState(null);

  const form = useForm(EMPTY_FORM, SCHEMA);
  const { run, saving } = useMutation({ onSuccess: () => list.reload() });

  const openCreate = () => {
    setEditing(null);
    form.reset(EMPTY_FORM);
    setFormOpen(true);
  };

  const openEdit = (subject) => {
    setEditing(subject);
    form.reset({
      name: subject.name ?? '',
      code: subject.code ?? '',
      categoryId: subject.category?.id ? String(subject.category.id) : '',
      active: subject.active !== false,
    });
    setFormOpen(true);
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!form.validateAll()) return;

    const payload = {
      name: form.values.name.trim(),
      code: form.values.code.trim() || null,
      // Only the id is sent; the server resolves the row, so a stale name in
      // the payload cannot rename a category as a side effect.
      category: form.values.categoryId ? { id: Number(form.values.categoryId) } : null,
      active: form.values.active,
    };

    const { ok } = await run(
      () => (editing ? subjects.update(editing.id, payload) : subjects.create(payload)),
      { successMessage: editing ? `${payload.name} updated.` : `${payload.name} added.` },
    );

    if (ok) setFormOpen(false);
  };

  const handleDelete = async () => {
    const { ok } = await run(() => subjects.remove(pendingDelete.id), {
      successMessage: `${pendingDelete.name} deleted.`,
    });
    if (ok) setPendingDelete(null);
  };

  const columns = [
    {
      key: 'name',
      header: 'Subject',
      render: (row) => (
        <span className="font-medium text-slate-800 dark:text-slate-100">{row.name}</span>
      ),
    },
    {
      key: 'code',
      header: 'Report heading',
      render: (row) => (
        <span className="text-slate-500 dark:text-slate-400">{orDash(row.code)}</span>
      ),
    },
    {
      key: 'category',
      header: 'Category',
      sortValue: (row) => row.category?.name ?? '',
      render: (row) => orDash(row.category?.name),
    },
    {
      key: 'active',
      header: 'Status',
      align: 'center',
      sortValue: (row) => (row.active === false ? 'Retired' : 'In use'),
      render: (row) => (
        <Badge tone={row.active === false ? 'neutral' : 'positive'}>
          {row.active === false ? 'Retired' : 'In use'}
        </Badge>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        eyebrow="Curriculum"
        title="Subjects"
        description="The subject list every class timetable and both subject reports are built from."
        icon={<NavIcon name="book" className="size-5" />}
        actions={
          privilege.insert && (
            <Button onClick={openCreate}>
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2.2"
                strokeLinecap="round"
                className="size-4"
                aria-hidden="true"
              >
                <path d="M12 5v14M5 12h14" />
              </svg>
              Add subject
            </Button>
          )
        }
      />

      <DataTable
        columns={columns}
        rows={list.data}
        loading={list.loading}
        searchPlaceholder="Search subject name or code…"
        emptyTitle="No subjects yet"
        emptyMessage="Add the curriculum subjects before setting up class timetables."
        initialSort={{ key: 'name', direction: 'asc' }}
        actions={(row) => (
          <RowActions
            onEdit={() => openEdit(row)}
            onDelete={() => setPendingDelete(row)}
            canEdit={privilege.update}
            canDelete={privilege.delete}
          />
        )}
      />

      <Drawer
        open={formOpen}
        onClose={() => setFormOpen(false)}
        title={editing ? `Edit ${editing.name}` : 'Add subject'}
        description="Subjects are shared across every grade; a class chooses from this list."
        size="md"
        footer={
          <>
            <Button variant="secondary" onClick={() => setFormOpen(false)} disabled={saving}>
              Cancel
            </Button>
            <Button type="submit" form="subject-form" loading={saving}>
              {editing ? 'Save changes' : 'Add subject'}
            </Button>
          </>
        }
      >
        <form id="subject-form" onSubmit={handleSubmit} noValidate>
          <FormSection title="Details" columns={1}>
            <TextField label="Subject name" required {...form.field('name')} />
            <TextField
              label="Report heading"
              hint="Optional short form used as the column heading when the full name will not fit, e.g. “Comb. Maths”."
              {...form.field('code')}
            />
            <SelectField
              label="Category"
              options={categoryList.data.map((item) => ({
                value: String(item.id),
                label: item.name,
              }))}
              placeholder="Ungrouped"
              hint="Groups the subject columns in the reports."
              {...form.field('categoryId')}
            />
            <Toggle
              label="In use"
              description="Retired subjects stay in past reports but cannot be added to a timetable."
              checked={form.values.active}
              onChange={(next) => form.setValue('active', next)}
            />
          </FormSection>
        </form>
      </Drawer>

      <ConfirmDialog
        open={!!pendingDelete}
        title="Delete this subject?"
        message={`${pendingDelete?.name ?? ''} will be removed completely. If it is already on a class timetable the delete is refused — retire it instead, so past reports keep their meaning.`}
        confirmLabel="Delete subject"
        loading={saving}
        onConfirm={handleDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </>
  );
}
