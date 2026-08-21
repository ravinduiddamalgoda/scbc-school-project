import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useMutation, useResource } from '@/hooks/useResource';
import { useForm } from '@/hooks/useForm';
import { useToast } from '@/context/ToastContext';
import { enrolments, lookups, payments, students } from '@/lib/resources';
import { saveBlob } from '@/lib/download';
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
 * This records money that came in; it is not a billing engine and it raises no
 * invoices. What it does now know is what a grade's fee is: "amount due" is
 * offered from the fee structure rather than typed on every receipt, which is
 * what made the outstanding-balance column disagree with itself.
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

  const toast = useToast();

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [pendingDelete, setPendingDelete] = useState(null);
  const [studentEnrolments, setStudentEnrolments] = useState([]);

  /**
   * The admission-number lookup, and the fee position it opens.
   *
   * The form used to offer a dropdown of every student in the school — nearly
   * three thousand of them — which is unusable when the clerk has a number
   * from a paper file and nothing else.
   */
  const [lookupQuery, setLookupQuery] = useState('');
  const [lookupResults, setLookupResults] = useState([]);
  const [lookingUp, setLookingUp] = useState(false);
  const [feePosition, setFeePosition] = useState(null);
  const [printing, setPrinting] = useState(false);

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

  /**
   * The fee position follows the chosen student.
   *
   * Loading it here rather than when the drawer opens means the "amount due"
   * box can be pre-filled with what is actually outstanding, and the history
   * panel below the table is showing the same student the form is about.
   */
  useEffect(() => {
    if (!selectedStudentId) {
      setFeePosition(null);
      return undefined;
    }

    let cancelled = false;
    payments
      .feePosition(Number(selectedStudentId))
      .then((position) => {
        if (!cancelled) setFeePosition(position);
      })
      .catch(() => {
        // No fee set for the grade is not an error: the form falls back to
        // asking for the amount, which is what it did before.
        if (!cancelled) setFeePosition(null);
      });

    return () => {
      cancelled = true;
    };
  }, [selectedStudentId]);

  const findStudent = async (event) => {
    event.preventDefault();
    if (!lookupQuery.trim()) return;

    setLookingUp(true);
    try {
      const found = await payments.findStudents(lookupQuery.trim());
      setLookupResults(found);
      if (found.length === 0) toast.error('No student matches that.');
      // One exact match is almost always the admission number just typed, so
      // it is selected rather than offered as a list of one.
      if (found.length === 1) chooseStudent(found[0]);
    } catch (error) {
      toast.error(error.message ?? 'The search failed.');
    } finally {
      setLookingUp(false);
    }
  };

  const chooseStudent = (row) => {
    form.setValue('studentId', row.id);
    setLookupResults([]);
  };

  const printHistory = async () => {
    setPrinting(true);
    try {
      const file = await payments.feePositionPdf(Number(selectedStudentId));
      saveBlob(file.blob, file.filename ?? 'Payment History.pdf');
    } catch (error) {
      toast.error(error.message ?? 'The history could not be produced.');
    } finally {
      setPrinting(false);
    }
  };

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
            {/*
              Admission-number lookup first, the full list second. The clerk
              almost always has the number; the dropdown is the fallback for
              when they only have a name they cannot spell.
            */}
            <div className="sm:col-span-2">
              <span className="mb-1.5 block text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                Find by admission number
              </span>
              <div className="flex gap-2">
                <input
                  type="search"
                  value={lookupQuery}
                  onChange={(event) => setLookupQuery(event.target.value)}
                  onKeyDown={(event) => {
                    // The drawer already has a submit button; Enter here must
                    // search rather than post a half-filled receipt.
                    if (event.key === 'Enter') findStudent(event);
                  }}
                  placeholder="e.g. 3960 — leading zeroes optional"
                  className="h-10 min-w-0 flex-1 rounded-lg border border-slate-300 bg-white px-3 text-sm text-slate-800 shadow-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
                />
                <Button type="button" variant="secondary" loading={lookingUp} onClick={findStudent}>
                  Find
                </Button>
              </div>

              {lookupResults.length > 1 && (
                <ul className="mt-2 max-h-44 divide-y divide-slate-200 overflow-y-auto rounded-lg border border-slate-200 dark:divide-slate-700 dark:border-slate-700">
                  {lookupResults.map((row) => (
                    <li key={row.id}>
                      <button
                        type="button"
                        onClick={() => chooseStudent(row)}
                        className="flex w-full items-center justify-between gap-3 px-3 py-2 text-left text-sm transition hover:bg-slate-50 dark:hover:bg-slate-800"
                      >
                        <span className="truncate">{row.fullname}</span>
                        <span className="shrink-0 text-xs text-slate-500">
                          {row.admissionNo} {row.grade ? `· ${row.grade}` : ''}
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            <SelectField
              label="Student"
              required
              options={studentOptions}
              placeholder={canReadStudents ? 'Select a student…' : 'No access to students'}
              disabled={!canReadStudents}
              className="sm:col-span-2"
              {...form.field('studentId')}
            />

            {feePosition && (
              <div className="sm:col-span-2 rounded-lg bg-slate-50 p-3 dark:bg-slate-800/60">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="text-xs text-slate-600 dark:text-slate-300">
                    <span className="font-medium">{feePosition.studentName}</span>
                    {feePosition.grade ? ` · ${feePosition.grade.name}` : ''}
                    {feePosition.academicYear ? ` · ${feePosition.academicYear.name}` : ''}
                  </p>
                  <Button
                    type="button"
                    variant="secondary"
                    loading={printing}
                    onClick={printHistory}
                  >
                    Payment history
                  </Button>
                </div>

                <dl className="mt-2 grid grid-cols-3 gap-2 text-xs">
                  <div>
                    <dt className="text-slate-500 dark:text-slate-400">Fee for the year</dt>
                    <dd className="tabular-nums font-medium text-slate-800 dark:text-slate-100">
                      {feePosition.annualFee === null ? 'not set' : money(feePosition.annualFee)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-slate-500 dark:text-slate-400">Paid so far</dt>
                    <dd className="tabular-nums font-medium text-slate-800 dark:text-slate-100">
                      {money(feePosition.totalPaid)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-slate-500 dark:text-slate-400">Balance</dt>
                    <dd className="tabular-nums font-medium text-slate-800 dark:text-slate-100">
                      {feePosition.balance === null ? '—' : money(feePosition.balance)}
                    </dd>
                  </div>
                </dl>

                {feePosition.annualFee !== null && !editing && (
                  <button
                    type="button"
                    onClick={() =>
                      form.setValue(
                        'amountDue',
                        // The outstanding balance, not the whole fee: a second
                        // instalment is due the remainder, not the lot again.
                        String(Math.max(0, Number(feePosition.balance ?? feePosition.annualFee))),
                      )
                    }
                    className="mt-2 text-xs font-medium text-brand-600 hover:underline dark:text-brand-400"
                  >
                    Use the outstanding balance as the amount due
                  </button>
                )}

                {feePosition.annualFee === null && (
                  <p className="mt-2 text-xs text-notice-600 dark:text-notice-500">
                    No fee has been set for this grade and year. Set it under Academic setup, or
                    type the amount due below.
                  </p>
                )}
              </div>
            )}
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
              hint="Offered from the grade's fee above; the balance is worked out from it."
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
