import { useCallback, useEffect, useState } from 'react';
import { useToast } from '@/context/ToastContext';
import { certificates } from '@/lib/resources';
import { saveBlob } from '@/lib/download';
import { formatDate } from '@/lib/format';

import Drawer from '@/components/ui/Drawer';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import { LoadingPanel } from '@/components/ui/Spinner';
import { FormSection } from '@/components/ui/PageHeader';
import { TextArea, TextField } from '@/components/ui/Field';

/**
 * Issue a leaving or character certificate for one student.
 *
 * The draft arrives with everything the school already records filled in, so
 * what is left on screen is only what is genuinely the principal's to write —
 * conduct, health observations, activities, the reason for leaving. That split
 * is the point: the old process retyped the whole form into Word, which is
 * where certificates carrying the wrong admission number came from.
 *
 * Issuing stores the wording as it stands. Reprints come back from that stored
 * text rather than being rebuilt, so a certificate presented years later is the
 * one that was signed.
 */
export default function CertificateDrawer({ open, student, onClose }) {
  const toast = useToast();

  const [type, setType] = useState('LEAVING');
  const [draft, setDraft] = useState(null);
  const [issued, setIssued] = useState([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const studentId = student?.id;

  const load = useCallback(async () => {
    if (!open || !studentId) return;

    setLoading(true);
    try {
      const [next, history] = await Promise.all([
        certificates.draft(studentId, type),
        certificates.list(studentId),
      ]);
      setDraft(next);
      setIssued(history);
    } catch (caught) {
      toast.error(caught.message);
      setDraft(null);
    } finally {
      setLoading(false);
    }
  }, [open, studentId, type, toast]);

  useEffect(() => {
    load();
  }, [load]);

  const set = (field) => (event) =>
    setDraft((current) => ({ ...current, [field]: event.target.value }));

  const handleIssue = async () => {
    setSaving(true);
    try {
      const record = await certificates.issue(draft);
      toast.success('Certificate issued. It can be reprinted from the list below.');
      setIssued((current) => [record, ...current]);
      // Download straight away: issuing one is almost always followed by
      // printing it.
      const { blob, filename } = await certificates.pdf(record.id);
      saveBlob(blob, filename ?? 'certificate.pdf');
    } catch (caught) {
      toast.error(caught.message);
    } finally {
      setSaving(false);
    }
  };

  const handleReprint = async (record) => {
    try {
      const { blob, filename } = await certificates.pdf(record.id);
      saveBlob(blob, filename ?? 'certificate.pdf');
    } catch (caught) {
      toast.error(caught.message);
    }
  };

  const leaving = type === 'LEAVING';

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title={`Certificates — ${student?.fullname ?? ''}`}
      description="Everything the school already records is filled in. Write the rest, then issue."
      size="xl"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={saving}>
            Close
          </Button>
          <Button onClick={handleIssue} loading={saving} disabled={!draft}>
            Issue &amp; download
          </Button>
        </>
      }
    >
      {/* ---- Which certificate ------------------------------------------- */}
      <div className="mb-5 flex gap-2">
        {[
          { value: 'LEAVING', label: 'Leaving certificate' },
          { value: 'CHARACTER', label: 'Character certificate' },
        ].map((option) => (
          <button
            key={option.value}
            type="button"
            onClick={() => setType(option.value)}
            className={[
              'rounded-lg px-3 py-2 text-sm font-medium transition',
              type === option.value
                ? 'bg-brand-600 text-white shadow-sm'
                : 'bg-slate-100 text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300',
            ].join(' ')}
          >
            {option.label}
          </button>
        ))}
      </div>

      {loading ? (
        <LoadingPanel label="Preparing the certificate" />
      ) : !draft ? null : leaving ? (
        <>
          <FormSection title="From the record" columns={2}>
            <TextField label="Name in full" value={draft.studentName ?? ''} onChange={set('studentName')} />
            <TextField
              label="Name with initials"
              value={draft.nameWithInitials ?? ''}
              onChange={set('nameWithInitials')}
            />
            <TextField label="Admission number" value={draft.admissionNo ?? ''} onChange={set('admissionNo')} />
            <TextField label="Religion" value={draft.religion ?? ''} onChange={set('religion')} />
            <TextField
              label="Date of admission"
              type="date"
              value={draft.date_of_admission ?? ''}
              onChange={set('date_of_admission')}
            />
            <TextField
              label="Date of leaving"
              type="date"
              value={draft.date_of_leaving ?? ''}
              onChange={set('date_of_leaving')}
            />
            <TextField
              label="Parent or guardian"
              value={draft.guardianName ?? ''}
              onChange={set('guardianName')}
            />
            <TextField
              label="Guardian's address"
              value={draft.guardianAddress ?? ''}
              onChange={set('guardianAddress')}
            />
            <TextField
              label="Last grade completed"
              value={draft.lastGradeCompleted ?? ''}
              onChange={set('lastGradeCompleted')}
            />
            <TextField
              label="Medium of instruction"
              value={draft.mediumOfInstruction ?? ''}
              onChange={set('mediumOfInstruction')}
            />
          </FormSection>

          <FormSection title="Written by the principal" columns={1}>
            <TextArea
              label="Subjects studied"
              rows={2}
              value={draft.subjectsStudied ?? ''}
              onChange={set('subjectsStudied')}
              hint="Taken from the student's enrolment; edit if the last grade differed."
            />
            <TextField
              label="Reason for leaving"
              value={draft.reasonForLeaving ?? ''}
              onChange={set('reasonForLeaving')}
            />
            <TextField label="Conduct and behaviour" value={draft.conduct ?? ''} onChange={set('conduct')} />
            <TextArea
              label="Weaknesses or health conditions identified"
              rows={2}
              value={draft.healthNotes ?? ''}
              onChange={set('healthNotes')}
            />
            <TextArea
              label="Co-curricular activities and leadership"
              rows={2}
              value={draft.coCurricular ?? ''}
              onChange={set('coCurricular')}
            />
            <TextArea
              label="Other special talents or abilities"
              rows={2}
              value={draft.specialTalents ?? ''}
              onChange={set('specialTalents')}
            />
            <TextField
              label="Principal's name"
              value={draft.principalName ?? ''}
              onChange={set('principalName')}
            />
          </FormSection>
        </>
      ) : (
        <FormSection title="The testimonial" columns={1}>
          <TextArea
            label="Body"
            rows={14}
            value={draft.body ?? ''}
            onChange={set('body')}
            hint="A starting point drafted from the record — pronouns follow the student's recorded gender. Edit freely; what you issue is what is stored and reprinted."
          />
          <TextField
            label="Last examination sat"
            value={draft.lastExamPassed ?? ''}
            onChange={set('lastExamPassed')}
            hint="e.g. G.C.E. Ordinary Level, 2026."
          />
          <TextField
            label="Principal's name"
            value={draft.principalName ?? ''}
            onChange={set('principalName')}
          />
        </FormSection>
      )}

      {/* ---- Already issued ---------------------------------------------- */}
      {issued.length > 0 && (
        <section className="mt-6 border-t border-slate-200 pt-4 dark:border-slate-800">
          <h3 className="mb-3 text-sm font-semibold text-slate-800 dark:text-slate-200">
            Already issued
          </h3>
          <ul className="divide-y divide-slate-100 dark:divide-slate-800">
            {issued.map((record) => (
              <li key={record.id} className="flex flex-wrap items-center gap-3 py-2.5">
                <Badge tone="neutral">
                  {record.type === 'CHARACTER' ? 'Character' : 'Leaving'}
                </Badge>
                <span className="min-w-0 flex-1 text-sm text-slate-600 dark:text-slate-300">
                  {record.studentName}
                  <span className="ml-2 text-xs text-slate-400">
                    issued {formatDate(record.issued_date)}
                  </span>
                </span>
                <Button size="sm" variant="secondary" onClick={() => handleReprint(record)}>
                  Reprint
                </Button>
              </li>
            ))}
          </ul>
        </section>
      )}
    </Drawer>
  );
}
