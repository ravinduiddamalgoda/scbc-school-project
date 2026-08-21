import { useEffect, useState } from 'react';
import { useToast } from '@/context/ToastContext';
import { feeStructures } from '@/lib/resources';

import SetupPanel from '@/components/ui/SetupPanel';
import Button from '@/components/ui/Button';
import EmptyState from '@/components/ui/EmptyState';
import { LoadingPanel } from '@/components/ui/Spinner';

/**
 * What each grade is charged for a year.
 *
 * "Amount due" was a number the clerk typed on every receipt. Nothing checked
 * it, nothing defaulted it, and two clerks recording the same grade's fee on
 * the same day could — and did — write different figures, which made the
 * outstanding-balance column of the Fees Details report meaningless.
 *
 * Per year as well as per grade, because a fee is set annually: holding only
 * the current figure would silently restate what last year's families owed the
 * moment the school raised it, and the receipts already issued would stop
 * adding up.
 */
export default function FeeStructurePanel({ rows, loading, yearId, privilege, onChanged }) {
  const toast = useToast();

  const [draft, setDraft] = useState({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setDraft(
      Object.fromEntries(
        rows.map((row) => [row.grade.id, { annualFee: row.annualFee ?? '', note: row.note ?? '' }]),
      ),
    );
  }, [rows]);

  const update = (gradeId, field, value) =>
    setDraft((current) => ({
      ...current,
      [gradeId]: { ...current[gradeId], [field]: value },
    }));

  const save = async () => {
    setSaving(true);
    try {
      await feeStructures.save(
        yearId || undefined,
        Object.entries(draft).map(([gradeId, row]) => ({
          grade: { id: Number(gradeId) },
          // An empty box means "the school has not set a fee", which the
          // payment form treats as "type the amount" rather than as zero.
          annualFee: row.annualFee === '' ? null : Number(row.annualFee),
          note: row.note?.trim() || null,
        })),
      );
      toast.success('Fees saved.');
      onChanged();
    } catch (error) {
      toast.error(error.message ?? 'The fees could not be saved.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <SetupPanel
      title="Fees"
      description="What each grade is charged for the year. The payment form offers it as the amount due."
      actions={
        privilege.update && (
          <Button loading={saving} onClick={save}>
            Save fees
          </Button>
        )
      }
    >
      {loading ? (
        <LoadingPanel label="Loading fees" />
      ) : rows.length === 0 ? (
        <div className="p-4">
          <EmptyState
            title="No grades yet"
            message="Seed the grade table before setting fees against it."
          />
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500 dark:bg-slate-800/60 dark:text-slate-400">
              <tr>
                <th className="px-4 py-2.5 text-left font-semibold">Grade</th>
                <th className="px-4 py-2.5 text-left font-semibold">Fee for the year</th>
                <th className="px-4 py-2.5 text-left font-semibold">What it covers</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
              {rows.map((row) => (
                <tr key={row.grade.id}>
                  <td className="px-4 py-2 text-slate-700 dark:text-slate-200">{row.grade.name}</td>
                  <td className="px-4 py-2">
                    <input
                      type="number"
                      min="0"
                      step="0.01"
                      placeholder="not set"
                      disabled={!privilege.update}
                      value={draft[row.grade.id]?.annualFee ?? ''}
                      onChange={(event) => update(row.grade.id, 'annualFee', event.target.value)}
                      className="h-9 w-40 rounded-lg border border-slate-300 bg-white px-2 text-sm tabular-nums dark:border-slate-700 dark:bg-slate-900"
                    />
                  </td>
                  <td className="px-4 py-2">
                    <input
                      type="text"
                      placeholder="Optional note"
                      disabled={!privilege.update}
                      value={draft[row.grade.id]?.note ?? ''}
                      onChange={(event) => update(row.grade.id, 'note', event.target.value)}
                      className="h-9 w-full rounded-lg border border-slate-300 bg-white px-2 text-sm dark:border-slate-700 dark:bg-slate-900"
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </SetupPanel>
  );
}
