import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useMutation, useResource } from '@/hooks/useResource';
import { useForm } from '@/hooks/useForm';
import { enrolments, lookups, payments, students } from '@/lib/resources';
import { formatDate, orDash, toDateInput } from '@/lib/format';
import { required } from '@/lib/validators';

import PageHeader, { FormSection } from '@/components/ui/PageHeader';
import DataTable from '@/components/ui/DataTable';
import Drawer from '@/components/ui/Drawer';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import Button from '@/components/ui/Button';
import RowActions from '@/components/ui/RowActions';
import { SelectField, TextField } from '@/components/ui/Field';
import { NavIcon } from '@/components/layout/navigation';

const EMPTY_FORM = {
  studentId: '',
  enrolmentId: '',
  paymentTypeId: '',
  amountDue: '',
  amountPaid: '',
  paidDate: '',
  billNo: '',
};

const SCHEMA = {
  studentId: [required('Student')],
  amountPaid: [required('Amount paid')],
  paidDate: [required('Paid date')],
};

const money = (value) =>
  value === null || value === undefined
    ? '—'
    : Number(value).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/**
 * Fee payments received.
 *
 * This records money that came in; it is not a billing engine. "Amount due" is
 * entered with the receipt rather than derived from a fee schedule, which is
 * all the Fees Details report needs — the fee-structure tables in the ER model
 * are not implemented.
 */
export default function PaymentPage() {
  const { can } = useAuth();
  const privilege = can('Payment');
  const canReadStudents = can('Student').select;

  const list = useResource(useCallback(() => payments.list(), []));
  const studentList = useResource(useCallback(() => students.list(), []), {
    enabled: canReadStudents,
  });
  const typeList = useResource(useCallback(() => lookups.paymentTypes(), []));

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [pendingDelete, setPendingDelete] = useState(null);
  const [studentEnrolments, setStudentEnrolments] = useState([]);

  const form = useForm(EMPTY_FORM, SCHEMA);
  const { run, saving } = useMutation({ onSuccess: () => list.reload() });

  // The enrolment list follows the chosen student: attaching a receipt to
  // somebody else's enrolment would file it under the wrong grade.
  const selectedStudentId = form.values.studentId;
  useEffect(() => {
    if (!selectedStudentId) {
      setStudentEnrolments([]);
      return undefined;
    }

    let cancelled = false;
    enrolments
      .list(Number(selectedStudentId))
      .then((rows) => {
        if (!cancelled) setStudentEnrolments(rows);
      })
      .catch(() => {
        if (!cancelled) setStudentEnrolments([]);
      });

    return () => {
      cancelled = true;
    };
  }, [selectedStudentId]);

  const studentOptions = useMemo(
    () =>
      studentList.data.map((student) => ({
        value: student.id,
        label: `${student.fullname} · ${student.stu_no ?? 'no admission no.'}`,
      })),
    [studentList.data],
  );

  const enrolmentOptions = useMemo(
    () =>
      studentEnrolments.map((row) => ({
        value: row.id,
        label: `${row.grade?.name ?? ''} ${row.classroom?.name ?? ''} · ${row.academicYear?.name ?? ''}`,
      })),
    [studentEnrolments],
  );

  const openCreate = () => {
    setEditing(null);
    form.reset({ ...EMPTY_FORM, paidDate: toDateInput(new Date()) });
    setFormOpen(true);
  };

  const openEdit = (payment) => {
    setEditing(payment);
    form.reset({
      studentId: payment.student?.id ?? '',
      enrolmentId: '',
      paymentTypeId: payment.paymentType?.id ?? '',
      amountDue: payment.amountDue ?? '',
      amountPaid: payment.amountPaid ?? '',
      paidDate: toDateInput(payment.paidDate),
      billNo: payment.billNo ?? '',
    });
    setFormOpen(true);
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!form.validateAll()) return;

    const payload = {
      studentId: Number(form.values.studentId),
      enrolmentId: form.values.enrolmentId ? Number(form.values.enrolmentId) : null,
      paymentTypeId: form.values.paymentTypeId ? Number(form.values.paymentTypeId) : null,
      amountDue: form.values.amountDue === '' ? null : Number(form.values.amountDue),
      amountPaid: Number(form.values.amountPaid),
      paidDate: form.values.paidDate,
      billNo: form.values.billNo.trim() || null,
    };

    const { ok } = await run(
      () => (editing ? payments.update(editing.id, payload) : payments.create(payload)),
      { successMessage: editing ? 'Receipt updated.' : 'Payment recorded.' },
    );
    if (ok) setFormOpen(false);
  };

  const columns = [
    { key: 'billNo', header: 'Receipt', render: (row) => orDash(row.billNo) },
    {
      key: 'student',
      header: 'Student',
      sortValue: (row) => row.student?.name ?? '',
      render: (row) => (
        <div className="min-w-0">
          <p className="truncate font-medium text-slate-800 dark:text-slate-100">
            {orDash(row.student?.name)}
          </p>
          <p className="truncate text-xs text-slate-400">{orDash(row.studentNo)}</p>
        </div>
      ),
    },
    {
      key: 'grade',
      header: 'Grade',
      sortValue: (row) => row.grade?.name ?? '',
      render: (row) => orDash(row.grade?.name),
    },
    {
      key: 'amountPaid',
      header: 'Paid',
      align: 'center',
      sortValue: (row) => Number(row.amountPaid ?? 0),
      render: (row) => <span className="tabular-nums">{money(row.amountPaid)}</span>,
    },
    {
      key: 'balance',
      header: 'Balance',
      align: 'center',
      sortValue: (row) => Number(row.balance ?? 0),
      render: (row) => <span className="tabular-nums">{money(row.balance)}</span>,
    },
    {
      key: 'paidDate',
      header: 'Paid on',
      sortValue: (row) => row.paidDate ?? '',
      render: (row) => formatDate(row.paidDate),
    },
  ];

  return (
    <>
      <PageHeader
        eyebrow="Fees"
        title="Payments"
        description="Receipts recorded against a student's enrolment — the source of the Fees Details report."
        icon={<NavIcon name="money" className="size-5" />}
        actions={privilege.insert && <Button onClick={openCreate}>Record payment</Button>}
      />

      <DataTable
        columns={columns}
        rows={list.data}
        loading={list.loading}
        searchPlaceholder="Search receipt, student or grade…"
        emptyTitle="No payments recorded"
        emptyMessage="Record the first receipt to start a fee history."
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
        title={editing ? `Edit receipt ${editing.billNo ?? ''}` : 'Record payment'}
        description="Link the payment to the enrolment it settles so the fee history shows the right grade."
        size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={() => setFormOpen(false)} disabled={saving}>
              Cancel
            </Button>
            <Button type="submit" form="payment-form" loading={saving}>
              Save
            </Button>
          </>
        }
      >
        <form id="payment-form" onSubmit={handleSubmit} noValidate>
          <FormSection title="Who">
            <SelectField
              label="Student"
              required
              options={studentOptions}
              placeholder={canReadStudents ? 'Select a student…' : 'No access to students'}
              disabled={!canReadStudents}
              className="sm:col-span-2"
              {...form.field('studentId')}
            />
            <SelectField
              label="For which enrolment"
              options={enrolmentOptions}
              placeholder={
                !form.values.studentId
                  ? 'Choose a student first'
                  : enrolmentOptions.length === 0
                    ? 'This student is not enrolled in any class'
                    : 'Not linked'
              }
              disabled={enrolmentOptions.length === 0}
              hint="Decides the grade and year this receipt appears under."
              className="sm:col-span-2"
              {...form.field('enrolmentId')}
            />
          </FormSection>

          <FormSection title="Amounts">
            <TextField
              label="Amount due"
              type="number"
              step="0.01"
              min="0"
              hint="Optional. The balance is worked out from this."
              {...form.field('amountDue')}
            />
            <TextField
              label="Amount paid"
              type="number"
              step="0.01"
              min="0"
              required
              {...form.field('amountPaid')}
            />
            <TextField label="Paid date" type="date" required {...form.field('paidDate')} />
            <SelectField
              label="Method"
              options={typeList.data.map((type) => ({ value: type.id, label: type.name }))}
              placeholder="Not recorded"
              {...form.field('paymentTypeId')}
            />
            <TextField
              label="Receipt number"
              hint="Leave blank to have one generated."
              className="sm:col-span-2"
              {...form.field('billNo')}
            />
          </FormSection>
        </form>
      </Drawer>

      <ConfirmDialog
        open={!!pendingDelete}
        title="Delete this receipt?"
        message={`Receipt ${pendingDelete?.billNo ?? ''} will be removed completely and will disappear from the student's fee history.`}
        confirmLabel="Delete receipt"
        loading={saving}
        onConfirm={async () => {
          const { ok } = await run(() => payments.remove(pendingDelete.id), {
            successMessage: 'Receipt deleted.',
          });
          if (ok) setPendingDelete(null);
        }}
        onCancel={() => setPendingDelete(null)}
      />
    </>
  );
}
