import { useCallback, useMemo, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useMutation, useResource } from '@/hooks/useResource';
import { useForm } from '@/hooks/useForm';
import { lookups, privileges } from '@/lib/resources';
import { orDash } from '@/lib/format';
import { required } from '@/lib/validators';

import PageHeader, { FormSection } from '@/components/ui/PageHeader';
import DataTable from '@/components/ui/DataTable';
import Drawer from '@/components/ui/Drawer';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import Button from '@/components/ui/Button';
import Badge, { BoolMark } from '@/components/ui/Badge';
import RowActions from '@/components/ui/RowActions';
import { Checkbox, SelectField } from '@/components/ui/Field';
import { NavIcon } from '@/components/layout/navigation';

const EMPTY_FORM = {
  roleId: '',
  moduleId: '',
  privilage_select: false,
  privilage_insert: false,
  privilage_update: false,
  privilage_delete: false,
};

const SCHEMA = {
  roleId: [required('Role')],
  moduleId: [required('Module')],
};

export default function PrivilegePage() {
  const { can } = useAuth();
  const privilege = can('Privilage');

  const list = useResource(useCallback(() => privileges.list(), []));
  const roleList = useResource(useCallback(() => lookups.assignableRoles(), []));
  const moduleList = useResource(useCallback(() => lookups.modules(), []));

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [pendingDelete, setPendingDelete] = useState(null);

  const form = useForm(EMPTY_FORM, SCHEMA);
  const { run, saving } = useMutation({ onSuccess: () => list.reload() });

  const roleOptions = useMemo(
    () => roleList.data.map((item) => ({ value: item.id, label: item.name })),
    [roleList.data],
  );

  const moduleOptions = useMemo(
    () => moduleList.data.map((item) => ({ value: item.id, label: item.name })),
    [moduleList.data],
  );

  const openCreate = () => {
    setEditing(null);
    form.reset(EMPTY_FORM);
    setFormOpen(true);
  };

  const openEdit = (row) => {
    setEditing(row);
    form.reset({
      roleId: row.role_id?.id ?? '',
      moduleId: row.module_id?.id ?? '',
      privilage_select: !!row.privilage_select,
      privilage_insert: !!row.privilage_insert,
      privilage_update: !!row.privilage_update,
      privilage_delete: !!row.privilage_delete,
    });
    setFormOpen(true);
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!form.validateAll()) return;

    const payload = {
      role_id: { id: Number(form.values.roleId) },
      module_id: { id: Number(form.values.moduleId) },
      privilage_select: form.values.privilage_select,
      privilage_insert: form.values.privilage_insert,
      privilage_update: form.values.privilage_update,
      privilage_delete: form.values.privilage_delete,
    };

    const { ok } = await run(
      () => (editing ? privileges.update(editing.id, payload) : privileges.create(payload)),
      { successMessage: editing ? 'Permissions updated.' : 'Permissions granted.' },
    );

    if (ok) setFormOpen(false);
  };

  const handleDelete = async () => {
    const { ok } = await run(() => privileges.remove(pendingDelete.id), {
      successMessage: 'All permissions revoked for that role and module.',
    });
    if (ok) setPendingDelete(null);
  };

  const columns = [
    {
      key: 'role',
      header: 'Role',
      sortValue: (row) => row.role_id?.name,
      render: (row) => <Badge tone="brand">{orDash(row.role_id?.name)}</Badge>,
    },
    {
      key: 'module',
      header: 'Module',
      sortValue: (row) => row.module_id?.name,
      render: (row) => (
        <span className="font-medium text-slate-800 dark:text-slate-100">
          {orDash(row.module_id?.name)}
        </span>
      ),
    },
    {
      key: 'privilage_select',
      header: 'View',
      align: 'center',
      sortValue: (row) => (row.privilage_select ? 1 : 0),
      render: (row) => <BoolMark value={row.privilage_select} label="View" />,
    },
    {
      key: 'privilage_insert',
      header: 'Create',
      align: 'center',
      sortValue: (row) => (row.privilage_insert ? 1 : 0),
      render: (row) => <BoolMark value={row.privilage_insert} label="Create" />,
    },
    {
      key: 'privilage_update',
      header: 'Edit',
      align: 'center',
      sortValue: (row) => (row.privilage_update ? 1 : 0),
      render: (row) => <BoolMark value={row.privilage_update} label="Edit" />,
    },
    {
      key: 'privilage_delete',
      header: 'Delete',
      align: 'center',
      sortValue: (row) => (row.privilage_delete ? 1 : 0),
      render: (row) => <BoolMark value={row.privilage_delete} label="Delete" />,
    },
  ];

  return (
    <>
      <PageHeader
        eyebrow="Administration"
        title="Permissions"
        description="What each role may do in each module."
        icon={<NavIcon name="shield" className="size-5" />}
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
              Grant permission
            </Button>
          )
        }
      />

      <div className="mb-4 flex items-start gap-2.5 rounded-panel bg-notice-50 p-3.5 text-sm text-notice-900 ring-1 ring-inset ring-notice-500/20 dark:bg-notice-900/25 dark:text-notice-500">
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.9"
          strokeLinecap="round"
          className="mt-0.5 size-4 shrink-0"
          aria-hidden="true"
        >
          <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0Z" />
          <path d="M12 9v4m0 4h.01" />
        </svg>
        <p>
          The built-in <strong>Admin</strong> account always holds every permission and is not
          listed here, so the system cannot be locked out of its own access management.
        </p>
      </div>

      <DataTable
        columns={columns}
        rows={list.data}
        loading={list.loading}
        searchPlaceholder="Search role or module…"
        emptyTitle="No permissions defined"
        emptyMessage="Grant a role access to a module to get started."
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
        title={editing ? 'Edit permissions' : 'Grant permission'}
        description="Each role gets one entry per module."
        size="md"
        footer={
          <>
            <Button variant="secondary" onClick={() => setFormOpen(false)} disabled={saving}>
              Cancel
            </Button>
            <Button type="submit" form="privilege-form" loading={saving}>
              {editing ? 'Save changes' : 'Grant permission'}
            </Button>
          </>
        }
      >
        <form id="privilege-form" onSubmit={handleSubmit} noValidate>
          <FormSection title="Scope" columns={1}>
            <SelectField label="Role" required options={roleOptions} {...form.field('roleId')} />
            <SelectField
              label="Module"
              required
              options={moduleOptions}
              {...form.field('moduleId')}
            />
          </FormSection>

          <FormSection title="Allowed actions" columns={1}>
            <Checkbox
              label="View"
              description="Read records and see the module in the menu."
              checked={form.values.privilage_select}
              onChange={(event) => form.setValue('privilage_select', event.target.checked)}
            />
            <Checkbox
              label="Create"
              description="Add new records."
              checked={form.values.privilage_insert}
              onChange={(event) => form.setValue('privilage_insert', event.target.checked)}
            />
            <Checkbox
              label="Edit"
              description="Modify existing records."
              checked={form.values.privilage_update}
              onChange={(event) => form.setValue('privilage_update', event.target.checked)}
            />
            <Checkbox
              label="Delete"
              description="Remove records."
              checked={form.values.privilage_delete}
              onChange={(event) => form.setValue('privilage_delete', event.target.checked)}
            />

            {!form.values.privilage_select &&
              (form.values.privilage_insert ||
                form.values.privilage_update ||
                form.values.privilage_delete) && (
                <p className="text-xs font-medium text-notice-600 dark:text-notice-500">
                  Without <strong>View</strong> the module stays hidden from the menu, so the other
                  permissions cannot be reached from the interface.
                </p>
              )}
          </FormSection>
        </form>
      </Drawer>

      <ConfirmDialog
        open={!!pendingDelete}
        title="Revoke these permissions?"
        message={`All four permissions for ${pendingDelete?.role_id?.name ?? 'this role'} on ${pendingDelete?.module_id?.name ?? 'this module'} will be turned off. The entry stays in the matrix so it can be granted again.`}
        confirmLabel="Revoke"
        loading={saving}
        onConfirm={handleDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </>
  );
}
