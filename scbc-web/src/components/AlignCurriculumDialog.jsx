import { useCallback, useEffect, useState } from 'react';
import { useToast } from '@/context/ToastContext';
import { classes } from '@/lib/resources';

import Drawer from '@/components/ui/Drawer';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import EmptyState from '@/components/ui/EmptyState';
import { LoadingPanel } from '@/components/ui/Spinner';

/**
 * Brings every class's timetable into line with its grade's curriculum.
 *
 * The curriculum records what each grade is taught, but nothing had ever
 * applied it to classes that already existed — which is why grade 1 classes
 * were carrying A/L subjects, and why those subjects were turning up on the
 * grade 1 mark sheet. Correcting eighty-five classes by hand was not a
 * reasonable thing to ask.
 *
 * It shows the difference before applying it, because removing a subject takes
 * its enrolments and any marks recorded against it, and marks are the one thing
 * nobody should lose as a side effect of tidying a timetable.
 */
export default function AlignCurriculumDialog({ open, yearId, onClose, onApplied }) {
  const toast = useToast();

  const [preview, setPreview] = useState(null);
  const [loading, setLoading] = useState(false);
  const [applying, setApplying] = useState(false);
  const [confirmed, setConfirmed] = useState(false);

  const load = useCallback(async () => {
    if (!open) return;

    setLoading(true);
    setConfirmed(false);
    try {
      setPreview(await classes.alignToCurriculum({ academicYearId: yearId || undefined }));
    } catch (error) {
      toast.error(error.message ?? 'The curriculum comparison failed.');
      setPreview(null);
    } finally {
      setLoading(false);
    }
  }, [open, yearId, toast]);

  useEffect(() => {
    load();
  }, [load]);

  const apply = async () => {
    setApplying(true);
    try {
      const result = await classes.alignToCurriculum({
        academicYearId: yearId || undefined,
        dryRun: false,
        // Only ever sent once the checkbox below has been ticked, and that
        // checkbox only appears when marks would actually be destroyed.
        force: confirmed,
      });
      toast.success(
        `${result.classesChanged} class(es) aligned — ${result.subjectsAdded} subject(s) added, ` +
          `${result.subjectsRemoved} removed.`,
      );
      onApplied?.();
      onClose();
    } catch (error) {
      toast.error(error.message ?? 'The timetables could not be aligned.');
    } finally {
      setApplying(false);
    }
  };

  const nothingToDo = preview && preview.classesChanged === 0;
  const destructive = preview && preview.marksAffected > 0;

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title="Align timetables to the curriculum"
      description="Sets each class's subjects to the ones its grade is taught."
      size="lg"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={applying}>
            Cancel
          </Button>
          {!nothingToDo && (
            <Button
              onClick={apply}
              loading={applying}
              disabled={loading || !preview || (destructive && !confirmed)}
            >
              Apply to {preview?.classesChanged ?? 0} class(es)
            </Button>
          )}
        </>
      }
    >
      {loading ? (
        <LoadingPanel label="Comparing timetables with the curriculum" />
      ) : !preview ? null : nothingToDo ? (
        <EmptyState
          title="Every timetable already matches"
          message="No class in this year is carrying a subject its grade does not take, and none is missing one."
        />
      ) : (
        <>
          <div className="mb-4 flex flex-wrap gap-2">
            <Badge tone="neutral">{preview.classesChanged} class(es) affected</Badge>
            <Badge tone="positive">{preview.subjectsAdded} subject(s) added</Badge>
            <Badge tone={preview.subjectsRemoved > 0 ? 'notice' : 'neutral'}>
              {preview.subjectsRemoved} removed
            </Badge>
          </div>

          {destructive && (
            <div className="mb-4 rounded-lg bg-negative-50 p-3 text-xs text-negative-700 dark:bg-negative-900/25 dark:text-negative-400">
              <p className="font-semibold">
                This would delete {preview.marksAffected} recorded mark(s).
              </p>
              <p className="mt-1">
                Some subjects being removed have marks against them. Removing the subject removes
                those marks with it, and they cannot be recovered.
              </p>
              <label className="mt-2 flex items-start gap-2">
                <input
                  type="checkbox"
                  checked={confirmed}
                  onChange={(event) => setConfirmed(event.target.checked)}
                  className="mt-0.5 size-4 shrink-0 rounded accent-negative-600"
                />
                <span>I have reviewed the list below and want those marks deleted.</span>
              </label>
            </div>
          )}

          <ul className="space-y-2">
            {preview.changes.map((change) => (
              <li
                key={change.classroomId}
                className="rounded-lg border border-slate-200 p-3 dark:border-slate-700"
              >
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <p className="text-sm font-medium text-slate-800 dark:text-slate-100">
                    {change.gradeName} · {change.className}
                  </p>
                  {change.marksAffected > 0 && (
                    <Badge tone="negative">{change.marksAffected} mark(s) lost</Badge>
                  )}
                </div>

                {change.added.length > 0 && (
                  <p className="mt-1 text-xs text-positive-700 dark:text-positive-500">
                    + {change.added.join(', ')}
                  </p>
                )}
                {change.removed.length > 0 && (
                  <p className="mt-1 text-xs text-negative-700 dark:text-negative-400">
                    − {change.removed.join(', ')}
                  </p>
                )}
              </li>
            ))}
          </ul>

          <p className="mt-4 rounded-lg bg-slate-50 p-3 text-xs text-slate-600 dark:bg-slate-800/60 dark:text-slate-300">
            Subjects are added without a teacher — the curriculum says what is taught, not by whom,
            so assign teachers on each class&rsquo;s timetable afterwards. Grade 12 and 13 classes
            are given every subject their grade offers across all three categories; trim each
            stream to its own five on the class timetable.
          </p>
        </>
      )}
    </Drawer>
  );
}
