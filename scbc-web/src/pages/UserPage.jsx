import { useCallback, useMemo, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useMutation, useResource } from '@/hooks/useResource';
import { useForm } from '@/hooks/useForm';
import { employees, lookups, users } from '@/lib/resources';
import { formatDate, orDash } from '@/lib/format';
import { matches, minLength, patterns, required } from '@/lib/validators';

import PageHeader, { DetailRow, FormSection } from '@/components/ui/PageHeader';
import DataTable from '@/components/ui/DataTable';
import Drawer from '@/components/ui/Drawer';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import Button from '@/components/ui/Button';
import Avatar from '@/components/ui/Avatar';
import Badge from '@/components/ui/Badge';
import RowActions from '@/components/ui/RowActions';
import { Checkbox, SelectField, TextArea, TextField, Toggle } from '@/components/ui/Field';
import { NavIcon } from '@/components/layout/navigation';

const EMPTY_FORM = {
  username: '',
  useremail: '',
  password: '',
  confirmPassword: '',
  status: true,
  note: '',
  employeeId: '',
  roleIds: [],
};

/**
 * The password rules differ between create and edit, so the schema is built
 * per mode rather than shared.
 */
function buildSchema(isEditing) {
  return {
    username: [required('Username'), minLength(3, 'Username')],
    useremail: [required('Email'), matches(patterns.email, 'Enter a valid email address.')],
    password: isEditing
      ? [minLength(5, 'Password')]
      : [required('Password'), minLength(5, 'Password')],
    confirmPassword: [
      (value, values) => {
        if (!values.password) return null; // Nothing to confirm.
        if (!value) return 'Re-enter the password.';
        return value === values.password ? null : 'The passwords do not match.';
      },
    ],
  };
}

export default function UserPage() {
  const { can, user: currentUser } = useAuth();
  const privilege = can('User');
  const canReadEmployees = can('Employee').select;

  const list = useResource(useCallback(() => users.list(), []));
  const roleList = useResource(useCallback(() => lookups.assignableRoles(), []));
  // Only staff without a login can be attached to a new account.
  const freeEmployeeList = useResource(useCallback(() => employees.withoutAccount(), []), {
    enabled: canReadEmployees,
  });

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [viewing, setViewing] = useState(null);
  const [pendingDelete, setPendingDelete] = useState(null);

  const schema = useMemo(() => buildSchema(!!editing), [editing]);
  const form = useForm(EMPTY_FORM, schema);
  const { run, saving } = useMutation({
    onSuccess: async () => {
      await list.reload();
      await freeEmployeeList.reload();
    },
  });

  const employeeOptions = useMemo(() => {
    const options = freeEmployeeList.data.map((item) => ({
      value: item.id,
      label: `${item.fullname} · ${item.emp_no ?? ''}`,
    }));

    // When editing, the linked employee is no longer "without account", so it
    // has to be re-added or the select would show it as unset.
    if (editing?.employee_id && !options.some((option) => option.value === editing.employee_id.id)) {
      options.unshift({
        value: editing.employee_id.id,
        label: `${editing.employee_id.fullname} · ${editing.employee_id.emp_no ?? ''}`,
      });
    }

    return options;
  }, [freeEmployeeList.data, editing]);

  const openCreate = () => {
    setEditing(null);
    form.reset(EMPTY_FORM);
    setFormOpen(true);
  };

  const openEdit = (user) => {
    setEditing(user);
    form.reset({
      username: user.username ?? '',
      useremail: user.useremail ?? '',
      password: '',
      confirmPassword: '',
      status: user.status ?? true,
      note: user.note ?? '',
      employeeId: user.employee_id?.id ?? '',
      roleIds: (user.roles ?? []).map((role) => role.id),
    });
    setFormOpen(true);
  };

  const toggleRole = (roleId) => {
    const current = form.values.roleIds;
    form.setValue(
      'roleIds',
      current.includes(roleId)
        ? current.filter((id) => id !== roleId)
        : [...current, roleId],
    );
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!form.validateAll()) return;

    if (form.values.roleIds.length === 0) {
      form.setFieldError('roleIds', 'Assign at least one role.');
      return;
    }

    const payload = {
      username: form.values.username.trim(),
      useremail: form.values.useremail.trim().toLowerCase(),
      // A blank password on edit means "keep the existing one".
      password: form.values.password ? form.values.password : null,
      status: form.values.status,
      note: form.values.note.trim() || null,
      employeeId: form.values.employeeId ? Number(form.values.employeeId) : null,
      roleIds: form.values.roleIds,
    };

    const { ok } = await run(
      () => (editing ? users.update(editing.id, payload) : users.create(payload)),
      {
        successMessage: editing
          ? `${payload.username} updated.`
          : `Account ${payload.username} created.`,
      },
    );

    if (ok) setFormOpen(false);
  };

  const handleDelete = async () => {
    const { ok } = await run(() => users.remove(pendingDelete.id), {
      successMessage: `${pendingDelete.username} was deactivated.`,
    });
    if (ok) setPendingDelete(null);
  };

  const columns = [
    {
      key: 'username',
      header: 'Account',
      render: (row) => (
        <div className="flex items-center gap-3">
          <Avatar src={row.userphoto} name={row.username} size="sm" />
          <div className="min-w-0">
            <p className="truncate font-medium text-slate-800 dark:text-slate-100">
              {row.username}
            </p>
            <p className="truncate text-xs text-slate-400">{row.useremail}</p>
          </div>
        </div>
      ),
    },
    {
      key: 'employee',
      header: 'Employee',
      sortValue: (row) => row.employee_id?.fullname,
      render: (row) => orDash(row.employee_id?.fullname),
    },
    {
      key: 'roles',
      header: 'Roles',
      sortable: false,
      render: (row) => (
        <div className="flex flex-wrap gap-1">
          {(row.roles ?? []).length === 0 ? (
            <span className="text-slate-400">—</span>
          ) : (
            row.roles.map((role) => (
              <Badge key={role.id} tone="brand">
                {role.name}
              </Badge>
            ))
          )}
        </div>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      align: 'center',
      sortValue: (row) => (row.status ? 1 : 0),
      render: (row) => (
        <Badge tone={row.status ? 'positive' : 'negative'}>
          {row.status ? 'Active' : 'Inactive'}
        </Badge>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        eyebrow="Administration"
        title="User accounts"
        description="Logins, role assignments and account status."
        icon={<NavIcon name="user" className="size-5" />}
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
              Create account
            </Button>
          )
        }
      />

      <div className="mb-4 flex items-start gap-2.5 rounded-panel bg-brand-50 p-3.5 text-sm text-brand-800 ring-1 ring-inset ring-brand-500/15 dark:bg-brand-950/50 dark:text-brand-200">
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.9"
          strokeLinecap="round"
          className="mt-0.5 size-4 shrink-0"
          aria-hidden="true"
        >
          <circle cx="12" cy="12" r="9" />
          <path d="M12 16v-4m0-4h.01" />
        </svg>
        <p>
          Your own account and the built-in <strong>Admin</strong> are not listed here. Edit your own
          details from <strong>My profile</strong>.
        </p>
      </div>

      <DataTable
        columns={columns}
        rows={list.data}
        loading={list.loading}
        searchPlaceholder="Search username or email…"
        emptyTitle="No other accounts"
        emptyMessage="Create an account so a staff member can sign in."
        actions={(row) => (
          <RowActions
            onView={() => setViewing(row)}
            onEdit={() => openEdit(row)}
            onDelete={() => setPendingDelete(row)}
            canEdit={privilege.update}
            canDelete={privilege.delete}
          />
        )}
      />

      {/* ---- Create / edit ------------------------------------------------ */}
      <Drawer
        open={formOpen}
        onClose={() => setFormOpen(false)}
        title={editing ? `Edit ${editing.username}` : 'Create account'}
        description={
          editing
            ? 'Leave the password blank to keep the current one.'
            : 'The account can sign in as soon as it is created.'
        }
        footer={
          <>
            <Button variant="secondary" onClick={() => setFormOpen(false)} disabled={saving}>
              Cancel
            </Button>
            <Button type="submit" form="user-form" loading={saving}>
              {editing ? 'Save changes' : 'Create account'}
            </Button>
          </>
        }
      >
        <form id="user-form" onSubmit={handleSubmit} noValidate>
          <FormSection title="Credentials" columns={1}>
            <TextField
              label="Username"
              required
              autoComplete="off"
              {...form.field('username')}
            />
            <TextField
              label="Email"
              type="email"
              required
              autoComplete="off"
              {...form.field('useremail')}
            />

            <div className="grid gap-4 sm:grid-cols-2">
              <TextField
                label={editing ? 'New password' : 'Password'}
                type="password"
                required={!editing}
                autoComplete="new-password"
                placeholder={editing ? 'Unchanged' : ''}
                {...form.field('password')}
              />
              <TextField
                label="Confirm password"
                type="password"
                required={!editing}
                autoComplete="new-password"
                placeholder={editing ? 'Unchanged' : ''}
                {...form.field('confirmPassword')}
              />
            </div>
          </FormSection>

          <FormSection title="Roles" columns={1}>
            {form.errors.roleIds && (
              <p className="text-xs font-medium text-negative-600 dark:text-negative-500">
                {form.errors.roleIds}
              </p>
            )}

            <div className="grid gap-2 sm:grid-cols-2">
              {roleList.data.map((role) => (
                <Checkbox
                  key={role.id}
                  label={role.name}
                  checked={form.values.roleIds.includes(role.id)}
                  onChange={() => toggleRole(role.id)}
                />
              ))}
            </div>

            {roleList.data.length === 0 && !roleList.loading && (
              <p className="text-sm text-slate-500 dark:text-slate-400">
                No assignable roles found. Seed the role table first.
              </p>
            )}
          </FormSection>

          <FormSection title="Linked record & status" columns={1}>
            <SelectField
              label="Employee"
              options={employeeOptions}
              placeholder={canReadEmployees ? 'Not linked' : 'No access to employees'}
              disabled={!canReadEmployees}
              hint="Only staff without an existing login are listed."
              {...form.field('employeeId')}
            />

            <Toggle
              label="Account active"
              description="Inactive accounts cannot sign in."
              checked={form.values.status}
              onChange={(value) => form.setValue('status', value)}
            />

            <TextArea label="Note" {...form.field('note')} />
          </FormSection>
        </form>
      </Drawer>

      {/* ---- Detail ------------------------------------------------------- */}
      <Drawer
        open={!!viewing}
        onClose={() => setViewing(null)}
        title={viewing?.username ?? ''}
        description={viewing?.useremail}
        footer={
          <Button variant="secondary" onClick={() => setViewing(null)}>
            Close
          </Button>
        }
      >
        {viewing && (
          <>
            <div className="mb-6 flex items-center gap-4">
              <Avatar src={viewing.userphoto} name={viewing.username} size="xl" />
              <div className="min-w-0">
                <p className="truncate text-lg font-semibold text-slate-900 dark:text-slate-50">
                  {viewing.username}
                </p>
                <p className="truncate text-sm text-slate-500 dark:text-slate-400">
                  {viewing.useremail}
                </p>
                <Badge tone={viewing.status ? 'positive' : 'negative'} className="mt-2">
                  {viewing.status ? 'Active' : 'Inactive'}
                </Badge>
              </div>
            </div>

            <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <DetailRow label="Roles" full>
                <div className="flex flex-wrap gap-1">
                  {(viewing.roles ?? []).map((role) => (
                    <Badge key={role.id} tone="brand">
                      {role.name}
                    </Badge>
                  ))}
                  {(viewing.roles ?? []).length === 0 && 'None'}
                </div>
              </DetailRow>
              <DetailRow label="Linked employee" full>
                {viewing.employee_id
                  ? `${viewing.employee_id.fullname} · ${orDash(viewing.employee_id.emp_no)}`
                  : 'Not linked'}
              </DetailRow>
              <DetailRow label="Created">{formatDate(viewing.added_datetime)}</DetailRow>
              <DetailRow label="Last updated">{formatDate(viewing.updatedatetime)}</DetailRow>
              <DetailRow label="Note" full>
                {orDash(viewing.note)}
              </DetailRow>
            </dl>
          </>
        )}
      </Drawer>

      <ConfirmDialog
        open={!!pendingDelete}
        title="Deactivate this account?"
        message={`${pendingDelete?.username ?? ''} will no longer be able to sign in. The record is kept and can be reactivated by editing it.`}
        confirmLabel="Deactivate"
        loading={saving}
        onConfirm={handleDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </>
  );
}
