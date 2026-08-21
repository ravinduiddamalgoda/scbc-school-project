import { useCallback, useMemo, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useMutation, useResource } from '@/hooks/useResource';
import { useForm } from '@/hooks/useForm';
import { guardians, lookups, students } from '@/lib/resources';
import { ageFrom, formatDate, orDash, toDateInput } from '@/lib/format';
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
import EnrolmentDrawer from '@/components/EnrolmentDrawer';
import CertificateDrawer from '@/components/CertificateDrawer';
import AchievementDrawer from '@/components/AchievementDrawer';

const RELIGIONS = ['Buddhism', 'Hinduism', 'Islam', 'Christianity', 'Other'];
const NATIONALITIES = ['Sinhalese', 'Tamil', 'Moor', 'Burgher', 'Malay', 'Other'];

const EMPTY_FORM = {
  fullname: '',
  callingname: '',
  birth_certi_no: '',
  nic: '',
  gender: '',
  dob: '',
  religion: '',
  nationality: '',
  previous_scl: '',
  address: '',
  note: '',
  gradeId: '',
  studentStatusId: '',
  guardianId: '',
  photo: null,
};

const SCHEMA = {
  fullname: [required('Full name'), matches(patterns.personName, 'Use letters only.')],
  callingname: [required('Calling name'), matches(patterns.personName, 'Use letters only.')],
  birth_certi_no: [
    required('Birth certificate number'),
    matches(patterns.birthCertificate, 'Use 6 to 12 letters, digits, / or -.'),
  ],
  // Optional: only older students hold an NIC.
  nic: [matches(patterns.nic, 'Use 9 digits + V, or 12 digits.')],
  gender: [required('Gender')],
  dob: [required('Date of birth'), notFuture('Date of birth'), ageBetween(3, 25, 'A student')],
  religion: [required('Religion')],
  nationality: [required('Nationality')],
  previous_scl: [required('Previous school'), maxLength(150, 'Previous school')],
  address: [required('Address'), maxLength(255, 'Address')],
  gradeId: [required('Grade')],
  studentStatusId: [required('Status')],
};

function toFormValues(student) {
  return {
    fullname: student.fullname ?? '',
    callingname: student.callingname ?? '',
    birth_certi_no: student.birth_certi_no ?? '',
    nic: student.nic ?? '',
    gender: student.gender ?? '',
    dob: toDateInput(student.dob),
    religion: student.religion ?? '',
    nationality: student.nationality ?? '',
    previous_scl: student.previous_scl ?? '',
    address: student.address ?? '',
    note: student.note ?? '',
    gradeId: student.grade_id?.id ?? '',
    studentStatusId: student.student_status_id?.id ?? '',
    guardianId: student.guardian_id?.id ?? '',
    photo: student.stu_photo ?? null,
  };
}

function toPayload(values) {
  return {
    fullname: values.fullname.trim(),
    callingname: values.callingname.trim(),
    birth_certi_no: values.birth_certi_no.trim(),
    nic: values.nic.trim() ? values.nic.trim().toUpperCase() : null,
    gender: values.gender,
    dob: values.dob,
    religion: values.religion,
    nationality: values.nationality,
    previous_scl: values.previous_scl.trim(),
    address: values.address.trim(),
    note: values.note.trim() || null,
    grade_id: { id: Number(values.gradeId) },
    student_status_id: { id: Number(values.studentStatusId) },
    guardian_id: values.guardianId ? { id: Number(values.guardianId) } : null,
    stu_photo: values.photo ?? null,
  };
}

function statusTone(name) {
  const value = (name ?? '').toLowerCase();
  if (value.includes('active') || value.includes('current')) return 'positive';
  if (value.includes('delete') || value.includes('left')) return 'negative';
  return 'notice';
}

export default function StudentPage() {
  const { can } = useAuth();
  const privilege = can('Student');
  const canReadGuardians = can('Guardian').select;

  const list = useResource(useCallback(() => students.list(), []));
  const gradeList = useResource(useCallback(() => lookups.grades(), []));
  const statusList = useResource(useCallback(() => lookups.studentStatuses(), []));
  const guardianList = useResource(useCallback(() => guardians.list(), []), {
    enabled: canReadGuardians,
  });

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [viewing, setViewing] = useState(null);
  const [pendingDelete, setPendingDelete] = useState(null);
  const [enrolling, setEnrolling] = useState(null);
  const [certifying, setCertifying] = useState(null);
  const [recording, setRecording] = useState(null);

  const form = useForm(EMPTY_FORM, SCHEMA);
  const { run, saving } = useMutation({ onSuccess: () => list.reload() });

  const gradeOptions = useMemo(
    () => gradeList.data.map((item) => ({ value: item.id, label: item.name })),
    [gradeList.data],
  );

  const statusOptions = useMemo(
    () => statusList.data.map((item) => ({ value: item.id, label: item.name })),
    [statusList.data],
  );

  const guardianOptions = useMemo(
    () =>
      guardianList.data.map((item) => ({
        value: item.id,
        label: `${item.fullname} · ${item.relationship ?? 'Guardian'} · ${item.mobile ?? ''}`,
      })),
    [guardianList.data],
  );

  const openCreate = () => {
    setEditing(null);
    form.reset(EMPTY_FORM);
    setFormOpen(true);
  };

  const openEdit = (student) => {
    setEditing(student);
    form.reset(toFormValues(student));
    setFormOpen(true);
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!form.validateAll()) return;

    const payload = toPayload(form.values);

    const { ok } = await run(
      () => (editing ? students.update(editing.id, payload) : students.create(payload)),
      {
        successMessage: editing
          ? `${payload.fullname} updated.`
          : `${payload.fullname} admitted. An admission number was assigned automatically.`,
      },
    );

    if (ok) setFormOpen(false);
  };

  const handleDelete = async () => {
    const { ok } = await run(() => students.remove(pendingDelete.id), {
      successMessage: `${pendingDelete.fullname} was removed.`,
    });
    if (ok) setPendingDelete(null);
  };

  const columns = [
    {
      key: 'fullname',
      header: 'Student',
      render: (row) => (
        <div className="flex items-center gap-3">
          <Avatar src={row.stu_photo} name={row.fullname} size="sm" />
          <div className="min-w-0">
            <p className="truncate font-medium text-slate-800 dark:text-slate-100">
              {row.fullname}
            </p>
            <p className="truncate text-xs text-slate-400">{row.callingname}</p>
          </div>
        </div>
      ),
    },
    { key: 'stu_no', header: 'Admission no.', render: (row) => orDash(row.stu_no) },
    {
      key: 'grade',
      header: 'Grade',
      sortValue: (row) => row.grade_id?.name,
      render: (row) => orDash(row.grade_id?.name),
    },
    {
      key: 'age',
      header: 'Age',
      align: 'center',
      sortValue: (row) => ageFrom(row.dob) ?? -1,
      render: (row) => {
        const age = ageFrom(row.dob);
        return age === null ? '—' : `${age}`;
      },
    },
    {
      key: 'guardian',
      header: 'Guardian',
      sortValue: (row) => row.guardian_id?.fullname,
      render: (row) => orDash(row.guardian_id?.fullname),
    },
    {
      key: 'status',
      header: 'Status',
      align: 'center',
      sortValue: (row) => row.student_status_id?.name,
      render: (row) => (
        <Badge tone={statusTone(row.student_status_id?.name)}>
          {orDash(row.student_status_id?.name)}
        </Badge>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        eyebrow="Students"
        title="Student register"
        description="Admissions, grades and guardian links."
        icon={<NavIcon name="students" className="size-5" />}
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
              Admit student
            </Button>
          )
        }
      />

      <DataTable
        columns={columns}
        rows={list.data}
        loading={list.loading}
        searchPlaceholder="Search name, admission or birth certificate no…"
        emptyTitle="No students yet"
        emptyMessage="Admit the first student to get started."
        actions={(row) => (
          <>
            <button
              type="button"
              onClick={() => setEnrolling(row)}
              title="Class enrolment"
              aria-label={`Class enrolment for ${row.fullname}`}
              className="rounded-lg p-2 text-slate-400 transition hover:bg-slate-100 hover:text-brand-600 dark:hover:bg-slate-800 dark:hover:text-brand-400"
            >
              <NavIcon name="book" className="size-4" />
            </button>
            <button
              type="button"
              onClick={() => setRecording(row)}
              title="Conduct & achievements"
              aria-label={`Conduct and achievements for ${row.fullname}`}
              className="rounded-lg p-2 text-slate-400 transition hover:bg-slate-100 hover:text-brand-600 dark:hover:bg-slate-800 dark:hover:text-brand-400"
            >
              <NavIcon name="star" className="size-4" />
            </button>
            <button
              type="button"
              onClick={() => setCertifying(row)}
              title="Certificates"
              aria-label={`Certificates for ${row.fullname}`}
              className="rounded-lg p-2 text-slate-400 transition hover:bg-slate-100 hover:text-brand-600 dark:hover:bg-slate-800 dark:hover:text-brand-400"
            >
              <NavIcon name="award" className="size-4" />
            </button>
            <RowActions
              onView={() => setViewing(row)}
              onEdit={() => openEdit(row)}
              onDelete={() => setPendingDelete(row)}
              canEdit={privilege.update}
              canDelete={privilege.delete}
            />
          </>
        )}
      />

      {/* ---- Create / edit ------------------------------------------------ */}
      <Drawer
        open={formOpen}
        onClose={() => setFormOpen(false)}
        title={editing ? `Edit ${editing.fullname}` : 'Admit student'}
        description={
          editing
            ? `Admission number ${editing.stu_no ?? '—'}`
            : 'The admission number is generated automatically on save.'
        }
        size="xl"
        footer={
          <>
            <Button variant="secondary" onClick={() => setFormOpen(false)} disabled={saving}>
              Cancel
            </Button>
            <Button type="submit" form="student-form" loading={saving}>
              {editing ? 'Save changes' : 'Admit student'}
            </Button>
          </>
        }
      >
        <form id="student-form" onSubmit={handleSubmit} noValidate>
          <FormSection title="Identity">
            <TextField label="Full name" required {...form.field('fullname')} />
            <TextField label="Calling name" required {...form.field('callingname')} />
            <TextField
              label="Birth certificate no."
              required
              placeholder="B/1234/2015"
              {...form.field('birth_certi_no')}
            />
            <TextField
              label="NIC"
              hint="Only for students who already hold one."
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
          </FormSection>

          <FormSection title="Background">
            <SelectField
              label="Religion"
              required
              options={RELIGIONS.map((value) => ({ value, label: value }))}
              {...form.field('religion')}
            />
            <SelectField
              label="Nationality"
              required
              options={NATIONALITIES.map((value) => ({ value, label: value }))}
              {...form.field('nationality')}
            />
            <TextField
              label="Previous school"
              required
              placeholder="None, if first admission"
              {...form.field('previous_scl')}
            />
            <TextArea label="Address" required className="sm:col-span-2" {...form.field('address')} />
          </FormSection>

          <FormSection title="Enrolment">
            <SelectField label="Grade" required options={gradeOptions} {...form.field('gradeId')} />
            <SelectField
              label="Status"
              required
              options={statusOptions}
              {...form.field('studentStatusId')}
            />
            <SelectField
              label="Guardian"
              options={guardianOptions}
              placeholder={canReadGuardians ? 'Not linked yet' : 'No access to guardians'}
              disabled={!canReadGuardians}
              hint={
                canReadGuardians
                  ? 'Register the guardian first if they are not listed.'
                  : undefined
              }
              className="sm:col-span-2"
              {...form.field('guardianId')}
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
        description={`Admission number ${viewing?.stu_no ?? '—'}`}
        footer={
          <Button variant="secondary" onClick={() => setViewing(null)}>
            Close
          </Button>
        }
      >
        {viewing && (
          <>
            <div className="mb-6 flex items-center gap-4">
              <Avatar src={viewing.stu_photo} name={viewing.fullname} size="xl" />
              <div className="min-w-0">
                <p className="truncate text-lg font-semibold text-slate-900 dark:text-slate-50">
                  {viewing.fullname}
                </p>
                <p className="truncate text-sm text-slate-500 dark:text-slate-400">
                  {orDash(viewing.grade_id?.name)}
                </p>
                <Badge tone={statusTone(viewing.student_status_id?.name)} className="mt-2">
                  {orDash(viewing.student_status_id?.name)}
                </Badge>
              </div>
            </div>

            <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <DetailRow label="Calling name">{orDash(viewing.callingname)}</DetailRow>
              <DetailRow label="Birth certificate no.">{orDash(viewing.birth_certi_no)}</DetailRow>
              <DetailRow label="NIC">{orDash(viewing.nic)}</DetailRow>
              <DetailRow label="Gender">{orDash(viewing.gender)}</DetailRow>
              <DetailRow label="Date of birth">
                {formatDate(viewing.dob)}
                {ageFrom(viewing.dob) !== null && (
                  <span className="ml-1.5 text-slate-400">({ageFrom(viewing.dob)} years)</span>
                )}
              </DetailRow>
              <DetailRow label="Religion">{orDash(viewing.religion)}</DetailRow>
              <DetailRow label="Nationality">{orDash(viewing.nationality)}</DetailRow>
              <DetailRow label="Previous school">{orDash(viewing.previous_scl)}</DetailRow>
              <DetailRow label="Guardian" full>
                {viewing.guardian_id ? (
                  <>
                    {viewing.guardian_id.fullname}
                    <span className="text-slate-400">
                      {' '}
                      · {orDash(viewing.guardian_id.relationship)} ·{' '}
                      {orDash(viewing.guardian_id.mobile)}
                    </span>
                  </>
                ) : (
                  'Not linked'
                )}
              </DetailRow>
              <DetailRow label="Address" full>
                {orDash(viewing.address)}
              </DetailRow>
              <DetailRow label="Note" full>
                {orDash(viewing.note)}
              </DetailRow>
            </dl>
          </>
        )}
      </Drawer>

      {/* ---- Class enrolment ---------------------------------------------- */}
      <EnrolmentDrawer
        student={enrolling}
        canEdit={privilege.update}
        onClose={() => setEnrolling(null)}
      />

      {/* ---- Conduct, health, leadership, co-curricular, talents ----------- */}
      <AchievementDrawer
        open={!!recording}
        student={recording}
        onClose={() => setRecording(null)}
      />

      {/* ---- Leaving and character certificates ---------------------------- */}
      <CertificateDrawer
        open={!!certifying}
        student={certifying}
        onClose={() => setCertifying(null)}
      />

      <ConfirmDialog
        open={!!pendingDelete}
        title="Remove this student?"
        message={`${pendingDelete?.fullname ?? ''} will be marked as deleted. The record is kept for audit purposes and can be restored by changing its status.`}
        confirmLabel="Remove student"
        loading={saving}
        onConfirm={handleDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </>
  );
}
