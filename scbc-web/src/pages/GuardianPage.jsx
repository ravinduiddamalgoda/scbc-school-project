import { useCallback, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useMutation, useResource } from '@/hooks/useResource';
import { useForm } from '@/hooks/useForm';
import { guardians } from '@/lib/resources';
import { orDash } from '@/lib/format';
import { matches, maxLength, patterns, required } from '@/lib/validators';

import PageHeader, { DetailRow, FormSection } from '@/components/ui/PageHeader';
import DataTable from '@/components/ui/DataTable';
import Drawer from '@/components/ui/Drawer';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import Button from '@/components/ui/Button';
import Avatar from '@/components/ui/Avatar';
import Badge from '@/components/ui/Badge';
import RowActions from '@/components/ui/RowActions';
import { SelectField, TextArea, TextField } from '@/components/ui/Field';
import { NavIcon } from '@/components/layout/navigation';

const RELATIONSHIPS = [
  'Father',
  'Mother',
  'Grandfather',
  'Grandmother',
  'Brother',
  'Sister',
  'Uncle',
  'Aunt',
  'Legal guardian',
  'Other',
];

const EMPTY_FORM = {
  fullname: '',
  nic: '',
  mobile: '',
  email: '',
  occupation: '',
  employer: '',
  address: '',
  relationship: '',
  s_g_name: '',
  s_g_mobile: '',
  s_g_relationship: '',
  s_g_email: '',
  s_g_address: '',
};

const SCHEMA = {
  fullname: [required('Full name'), matches(patterns.personName, 'Use letters only.')],
  nic: [required('NIC'), matches(patterns.nic, 'Use 9 digits + V, or 12 digits.')],
  mobile: [required('Mobile number'), matches(patterns.phone, 'Use 10 digits, e.g. 0771234567.')],
  email: [matches(patterns.email, 'Enter a valid email address.')],
  address: [required('Address'), maxLength(255, 'Address')],
  relationship: [required('Relationship')],
  s_g_name: [matches(patterns.personName, 'Use letters only.')],
  s_g_mobile: [matches(patterns.phone, 'Use 10 digits, e.g. 0771234567.')],
  s_g_email: [matches(patterns.email, 'Enter a valid email address.')],
};

function toFormValues(guardian) {
  return Object.fromEntries(
    Object.keys(EMPTY_FORM).map((key) => [key, guardian[key] ?? '']),
  );
}

function toPayload(values) {
  const blankToNull = (value) => (value.trim() === '' ? null : value.trim());

  return {
    fullname: values.fullname.trim(),
    nic: values.nic.trim().toUpperCase(),
    mobile: values.mobile.trim(),
    email: blankToNull(values.email),
    occupation: blankToNull(values.occupation),
    employer: blankToNull(values.employer),
    address: values.address.trim(),
    relationship: values.relationship,
    s_g_name: blankToNull(values.s_g_name),
    s_g_mobile: blankToNull(values.s_g_mobile),
    s_g_relationship: blankToNull(values.s_g_relationship),
    s_g_email: blankToNull(values.s_g_email),
    s_g_address: blankToNull(values.s_g_address),
  };
}

export default function GuardianPage() {
  const { can } = useAuth();
  const privilege = can('Guardian');

  const list = useResource(useCallback(() => guardians.list(), []));

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [viewing, setViewing] = useState(null);
  const [pendingDelete, setPendingDelete] = useState(null);

  const form = useForm(EMPTY_FORM, SCHEMA);
  const { run, saving } = useMutation({ onSuccess: () => list.reload() });

  const openCreate = () => {
    setEditing(null);
    form.reset(EMPTY_FORM);
    setFormOpen(true);
  };

  const openEdit = (guardian) => {
    setEditing(guardian);
    form.reset(toFormValues(guardian));
    setFormOpen(true);
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!form.validateAll()) return;

    const payload = toPayload(form.values);

    const { ok } = await run(
      () => (editing ? guardians.update(editing.id, payload) : guardians.create(payload)),
      {
        successMessage: editing
          ? `${payload.fullname} updated.`
          : `${payload.fullname} registered as a guardian.`,
      },
    );

    if (ok) setFormOpen(false);
  };

  const handleDelete = async () => {
    const { ok } = await run(() => guardians.remove(pendingDelete.id), {
      successMessage: `${pendingDelete.fullname} was removed.`,
    });
    if (ok) setPendingDelete(null);
  };

  const columns = [
    {
      key: 'fullname',
      header: 'Guardian',
      render: (row) => (
        <div className="flex items-center gap-3">
          <Avatar name={row.fullname} size="sm" />
          <div className="min-w-0">
            <p className="truncate font-medium text-slate-800 dark:text-slate-100">
              {row.fullname}
            </p>
            <p className="truncate text-xs text-slate-400">{orDash(row.occupation)}</p>
          </div>
        </div>
      ),
    },
    { key: 'guardian_no', header: 'Reference', render: (row) => orDash(row.guardian_no) },
    { key: 'nic', header: 'NIC', render: (row) => orDash(row.nic) },
    {
      key: 'relationship',
      header: 'Relationship',
      align: 'center',
      render: (row) => <Badge tone="brand">{orDash(row.relationship)}</Badge>,
    },
    { key: 'mobile', header: 'Mobile', render: (row) => orDash(row.mobile) },
    {
      key: 's_g_name',
      header: 'Secondary contact',
      render: (row) => orDash(row.s_g_name),
    },
  ];

  return (
    <>
      <PageHeader
        eyebrow="Students"
        title="Guardians"
        description="Parents and legal guardians, with a secondary contact for each."
        icon={<NavIcon name="guardian" className="size-5" />}
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
              Register guardian
            </Button>
          )
        }
      />

      <DataTable
        columns={columns}
        rows={list.data}
        loading={list.loading}
        searchPlaceholder="Search name, NIC or mobile…"
        emptyTitle="No guardians yet"
        emptyMessage="Register a guardian before linking them to a student."
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
        title={editing ? `Edit ${editing.fullname}` : 'Register guardian'}
        description={
          editing
            ? `Reference ${editing.guardian_no ?? '—'}`
            : 'The reference number is generated automatically on save.'
        }
        size="xl"
        footer={
          <>
            <Button variant="secondary" onClick={() => setFormOpen(false)} disabled={saving}>
              Cancel
            </Button>
            <Button type="submit" form="guardian-form" loading={saving}>
              {editing ? 'Save changes' : 'Register guardian'}
            </Button>
          </>
        }
      >
        <form id="guardian-form" onSubmit={handleSubmit} noValidate>
          <FormSection title="Primary guardian">
            <TextField label="Full name" required {...form.field('fullname')} />
            <TextField label="NIC" required {...form.field('nic')} />
            <SelectField
              label="Relationship to student"
              required
              options={RELATIONSHIPS.map((value) => ({ value, label: value }))}
              {...form.field('relationship')}
            />
            <TextField
              label="Mobile"
              required
              placeholder="0771234567"
              {...form.field('mobile')}
            />
            <TextField label="Email" type="email" {...form.field('email')} />
            <TextField label="Occupation" {...form.field('occupation')} />
            <TextField label="Employer" {...form.field('employer')} />
            <TextArea label="Address" required className="sm:col-span-2" {...form.field('address')} />
          </FormSection>

          <FormSection
            title="Secondary contact"
            description="Used when the primary guardian cannot be reached. Optional."
          >
            <TextField label="Full name" {...form.field('s_g_name')} />
            <SelectField
              label="Relationship to student"
              options={RELATIONSHIPS.map((value) => ({ value, label: value }))}
              {...form.field('s_g_relationship')}
            />
            <TextField label="Mobile" {...form.field('s_g_mobile')} />
            <TextField label="Email" type="email" {...form.field('s_g_email')} />
            <TextArea label="Address" className="sm:col-span-2" {...form.field('s_g_address')} />
          </FormSection>
        </form>
      </Drawer>

      {/* ---- Detail ------------------------------------------------------- */}
      <Drawer
        open={!!viewing}
        onClose={() => setViewing(null)}
        title={viewing?.fullname ?? ''}
        description={`Reference ${viewing?.guardian_no ?? '—'}`}
        footer={
          <Button variant="secondary" onClick={() => setViewing(null)}>
            Close
          </Button>
        }
      >
        {viewing && (
          <>
            <h3 className="mb-3 text-sm font-semibold text-slate-800 dark:text-slate-200">
              Primary guardian
            </h3>
            <dl className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-2">
              <DetailRow label="NIC">{orDash(viewing.nic)}</DetailRow>
              <DetailRow label="Relationship">{orDash(viewing.relationship)}</DetailRow>
              <DetailRow label="Mobile">{orDash(viewing.mobile)}</DetailRow>
              <DetailRow label="Email">{orDash(viewing.email)}</DetailRow>
              <DetailRow label="Occupation">{orDash(viewing.occupation)}</DetailRow>
              <DetailRow label="Employer">{orDash(viewing.employer)}</DetailRow>
              <DetailRow label="Address" full>
                {orDash(viewing.address)}
              </DetailRow>
            </dl>

            <h3 className="mb-3 border-t border-slate-200 pt-5 text-sm font-semibold text-slate-800 dark:border-slate-800 dark:text-slate-200">
              Secondary contact
            </h3>
            <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <DetailRow label="Full name">{orDash(viewing.s_g_name)}</DetailRow>
              <DetailRow label="Relationship">{orDash(viewing.s_g_relationship)}</DetailRow>
              <DetailRow label="Mobile">{orDash(viewing.s_g_mobile)}</DetailRow>
              <DetailRow label="Email">{orDash(viewing.s_g_email)}</DetailRow>
              <DetailRow label="Address" full>
                {orDash(viewing.s_g_address)}
              </DetailRow>
            </dl>
          </>
        )}
      </Drawer>

      <ConfirmDialog
        open={!!pendingDelete}
        title="Remove this guardian?"
        message={`${pendingDelete?.fullname ?? ''} will be deleted permanently. If any student is still linked to this guardian the removal is refused.`}
        confirmLabel="Remove guardian"
        loading={saving}
        onConfirm={handleDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </>
  );
}
