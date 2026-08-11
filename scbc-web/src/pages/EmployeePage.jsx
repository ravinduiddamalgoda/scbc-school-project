import { useCallback, useMemo, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useMutation, useResource } from '@/hooks/useResource';
import { useForm } from '@/hooks/useForm';
import { employees, lookups } from '@/lib/resources';
import { formatDate, orDash, toDateInput } from '@/lib/format';
import {
  ageBetween,
  matches,
  maxLength,
  notFuture,
  patterns,
  required,
} from '@/lib/validators';

import PageHeader, { DetailRow, FormSection } from '@/components/ui/PageHeader';
import DataTable from '@/components/ui/DataTable';
import Drawer from '@/components/ui/Drawer';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import Button from '@/components/ui/Button';
import Avatar from '@/components/ui/Avatar';
import Badge from '@/components/ui/Badge';
import RowActions from '@/components/ui/RowActions';
import PhotoPicker from '@/components/ui/PhotoPicker';
import { RadioGroup, SelectField, TextArea, TextField } from '@/components/ui/Field';
import { NavIcon } from '@/components/layout/navigation';

const CIVIL_STATUSES = ['Single', 'Married', 'Divorced', 'Widowed'];

const EMPTY_FORM = {
  fullname: '',
  callingname: '',
  nic: '',
  gender: '',
  dob: '',
  email: '',
  civilstatus: '',
  mobileno: '',
  landno: '',
  address: '',
  note: '',
  designationId: '',
  statusId: '',
  photo: null,
};

const SCHEMA = {
  fullname: [required('Full name'), matches(patterns.personName, 'Use letters only.')],
  callingname: [required('Calling name'), matches(patterns.personName, 'Use letters only.')],
  nic: [required('NIC'), matches(patterns.nic, 'Use 9 digits + V, or 12 digits.')],
  gender: [required('Gender')],
  dob: [required('Date of birth'), notFuture('Date of birth'), ageBetween(16, 80, 'An employee')],
  email: [required('Email'), matches(patterns.email, 'Enter a valid email address.')],
  civilstatus: [required('Civil status')],
  mobileno: [required('Mobile number'), matches(patterns.phone, 'Use 10 digits, e.g. 0771234567.')],
  landno: [matches(patterns.phone, 'Use 10 digits, e.g. 0812234567.')],
  address: [required('Address'), maxLength(255, 'Address')],
  designationId: [required('Designation')],
  statusId: [required('Status')],
};

/** Maps an API record onto the flat shape the form works with. */
function toFormValues(employee) {
  return {
    fullname: employee.fullname ?? '',
    callingname: employee.callingname ?? '',
    nic: employee.nic ?? '',
    gender: employee.gender ?? '',
    dob: toDateInput(employee.dob),
    email: employee.email ?? '',
    civilstatus: employee.civilstatus ?? '',
    mobileno: employee.mobileno ?? '',
    landno: employee.landno ?? '',
    address: employee.address ?? '',
    note: employee.note ?? '',
    designationId: employee.designation_id?.id ?? '',
    statusId: employee.status_id?.id ?? '',
    photo: employee.emp_photo ?? null,
  };
}

/** Maps form values back onto the entity shape the API expects. */
function toPayload(values) {
  return {
    fullname: values.fullname.trim(),
    callingname: values.callingname.trim(),
    nic: values.nic.trim().toUpperCase(),
    gender: values.gender,
    dob: values.dob,
    email: values.email.trim().toLowerCase(),
    civilstatus: values.civilstatus,
    mobileno: values.mobileno.trim(),
    landno: values.landno.trim() || null,
    address: values.address.trim(),
    note: values.note.trim() || null,
    designation_id: { id: Number(values.designationId) },
    status_id: { id: Number(values.statusId) },
    emp_photo: values.photo ?? null,
  };
}

function statusTone(name) {
  const value = (name ?? '').toLowerCase();
  if (value.includes('active')) return 'positive';
  if (value.includes('delete')) return 'negative';
  return 'notice';
}

export default function EmployeePage() {
  const { can } = useAuth();
  const privilege = can('Employee');

  const list = useResource(useCallback(() => employees.list(), []));
  const designationList = useResource(useCallback(() => lookups.designations(), []));
  const statusList = useResource(useCallback(() => lookups.statuses(), []));

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null); // null = creating
  const [viewing, setViewing] = useState(null);
  const [pendingDelete, setPendingDelete] = useState(null);

  const form = useForm(EMPTY_FORM, SCHEMA);
  const { run, saving } = useMutation({ onSuccess: () => list.reload() });

  const designationOptions = useMemo(
    () => designationList.data.map((item) => ({ value: item.id, label: item.name })),
    [designationList.data],
  );

  const statusOptions = useMemo(
    () => statusList.data.map((item) => ({ value: item.id, label: item.name })),
    [statusList.data],
  );

  const openCreate = () => {
    setEditing(null);
    form.reset(EMPTY_FORM);
    setFormOpen(true);
  };

  const openEdit = (employee) => {
    setEditing(employee);
    form.reset(toFormValues(employee));
    setFormOpen(true);
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!form.validateAll()) return;

    const payload = toPayload(form.values);

    const { ok } = await run(
      () =>
        editing
          ? employees.update(editing.id, payload)
          : employees.create(payload),
      {
        successMessage: editing
          ? `${payload.fullname} updated.`
          : `${payload.fullname} added. A staff number was assigned automatically.`,
      },
    );

    if (ok) setFormOpen(false);
  };

  const handleDelete = async () => {
    const { ok } = await run(() => employees.remove(pendingDelete.id), {
      successMessage: `${pendingDelete.fullname} was removed.`,
    });
    if (ok) setPendingDelete(null);
  };

  const columns = [
    {
      key: 'fullname',
      header: 'Employee',
      render: (row) => (
        <div className="flex items-center gap-3">
          <Avatar src={row.emp_photo} name={row.fullname} size="sm" />
          <div className="min-w-0">
            <p className="truncate font-medium text-slate-800 dark:text-slate-100">
              {row.fullname}
            </p>
            <p className="truncate text-xs text-slate-400">{row.callingname}</p>
          </div>
        </div>
      ),
    },
    { key: 'emp_no', header: 'Staff no.', render: (row) => orDash(row.emp_no) },
    { key: 'nic', header: 'NIC', render: (row) => orDash(row.nic) },
    {
      key: 'designation',
      header: 'Designation',
      sortValue: (row) => row.designation_id?.name,
      render: (row) => orDash(row.designation_id?.name),
    },
    { key: 'mobileno', header: 'Mobile', render: (row) => orDash(row.mobileno) },
    {
      key: 'status',
      header: 'Status',
      align: 'center',
      sortValue: (row) => row.status_id?.name,
      render: (row) => (
        <Badge tone={statusTone(row.status_id?.name)}>{orDash(row.status_id?.name)}</Badge>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        eyebrow="Administration"
        title="Employees"
        description="Staff records, designations and employment status."
        icon={<NavIcon name="employee" className="size-5" />}
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
              Add employee
            </Button>
          )
        }
      />

      <DataTable
        columns={columns}
        rows={list.data}
        loading={list.loading}
        searchPlaceholder="Search name, NIC or staff number…"
        emptyTitle="No employees yet"
        emptyMessage="Add the first staff record to get started."
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
        title={editing ? `Edit ${editing.fullname}` : 'Add employee'}
        description={
          editing
            ? `Staff number ${editing.emp_no ?? '—'}`
            : 'The staff number is generated automatically on save.'
        }
        size="xl"
        footer={
          <>
            <Button variant="secondary" onClick={() => setFormOpen(false)} disabled={saving}>
              Cancel
            </Button>
            <Button type="submit" form="employee-form" loading={saving}>
              {editing ? 'Save changes' : 'Add employee'}
            </Button>
          </>
        }
      >
        <form id="employee-form" onSubmit={handleSubmit} noValidate>
          <FormSection title="Identity">
            <TextField label="Full name" required {...form.field('fullname')} />
            <TextField label="Calling name" required {...form.field('callingname')} />
            <TextField
              label="NIC"
              required
              placeholder="200012345678"
              {...form.field('nic')}
            />
            <TextField label="Date of birth" type="date" required {...form.field('dob')} />

            <RadioGroup
              label="Gender"
              required
              name="gender"
              value={form.values.gender}
              onChange={form.handleChange('gender')}
              error={form.touched.gender ? form.errors.gender : undefined}
              options={[
                { value: 'Male', label: 'Male' },
                { value: 'Female', label: 'Female' },
              ]}
            />

            <SelectField
              label="Civil status"
              required
              options={CIVIL_STATUSES.map((value) => ({ value, label: value }))}
              {...form.field('civilstatus')}
            />
          </FormSection>

          <FormSection title="Contact">
            <TextField label="Email" type="email" required {...form.field('email')} />
            <TextField
              label="Mobile"
              required
              placeholder="0771234567"
              {...form.field('mobileno')}
            />
            <TextField label="Landline" placeholder="0812234567" {...form.field('landno')} />
            <TextArea label="Address" required className="sm:col-span-2" {...form.field('address')} />
          </FormSection>

          <FormSection title="Employment">
            <SelectField
              label="Designation"
              required
              options={designationOptions}
              hint="Designations flagged for logins provision an account automatically."
              {...form.field('designationId')}
            />
            <SelectField
              label="Status"
              required
              options={statusOptions}
              {...form.field('statusId')}
            />
            <TextArea label="Note" className="sm:col-span-2" {...form.field('note')} />
          </FormSection>

          <FormSection title="Photo" columns={1}>
            <PhotoPicker
              value={form.values.photo}
              name={form.values.fullname}
              onChange={(photo) => form.setValue('photo', photo)}
            />
          </FormSection>
        </form>
      </Drawer>

      {/* ---- Detail ------------------------------------------------------- */}
      <Drawer
        open={!!viewing}
        onClose={() => setViewing(null)}
        title={viewing?.fullname ?? ''}
        description={`Staff number ${viewing?.emp_no ?? '—'}`}
        footer={
          <Button variant="secondary" onClick={() => setViewing(null)}>
            Close
          </Button>
        }
      >
        {viewing && (
          <>
            <div className="mb-6 flex items-center gap-4">
              <Avatar src={viewing.emp_photo} name={viewing.fullname} size="xl" />
              <div className="min-w-0">
                <p className="truncate text-lg font-semibold text-slate-900 dark:text-slate-50">
                  {viewing.fullname}
                </p>
                <p className="truncate text-sm text-slate-500 dark:text-slate-400">
                  {orDash(viewing.designation_id?.name)}
                </p>
                <Badge tone={statusTone(viewing.status_id?.name)} className="mt-2">
                  {orDash(viewing.status_id?.name)}
                </Badge>
              </div>
            </div>

            <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <DetailRow label="Calling name">{orDash(viewing.callingname)}</DetailRow>
              <DetailRow label="NIC">{orDash(viewing.nic)}</DetailRow>
              <DetailRow label="Gender">{orDash(viewing.gender)}</DetailRow>
              <DetailRow label="Date of birth">{formatDate(viewing.dob)}</DetailRow>
              <DetailRow label="Civil status">{orDash(viewing.civilstatus)}</DetailRow>
              <DetailRow label="Email">{orDash(viewing.email)}</DetailRow>
              <DetailRow label="Mobile">{orDash(viewing.mobileno)}</DetailRow>
              <DetailRow label="Landline">{orDash(viewing.landno)}</DetailRow>
              <DetailRow label="Address" full>
                {orDash(viewing.address)}
              </DetailRow>
              <DetailRow label="Note" full>
                {orDash(viewing.note)}
              </DetailRow>
              <DetailRow label="Added">{formatDate(viewing.added_datetime)}</DetailRow>
              <DetailRow label="Last updated">{formatDate(viewing.updated_datetime)}</DetailRow>
            </dl>
          </>
        )}
      </Drawer>

      <ConfirmDialog
        open={!!pendingDelete}
        title="Remove this employee?"
        message={`${pendingDelete?.fullname ?? ''} will be marked as deleted. The record is kept for audit purposes and can be restored by changing its status.`}
        confirmLabel="Remove employee"
        loading={saving}
        onConfirm={handleDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </>
  );
}
