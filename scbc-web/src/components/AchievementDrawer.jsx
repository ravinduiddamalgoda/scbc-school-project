import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/context/ToastContext';
import { achievements } from '@/lib/resources';

import Drawer from '@/components/ui/Drawer';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import EmptyState from '@/components/ui/EmptyState';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import { LoadingPanel } from '@/components/ui/Spinner';
import { SelectField, TextArea, TextField } from '@/components/ui/Field';

/**
 * The four things the leaving certificate asks for that are not facts the
 * school already holds: conduct, health observations, leadership and
 * co-curricular activity, and other talents.
 *
 * Recorded here as they happen, over the student's years at the school, so the
 * certificate can be drafted from a record instead of from whatever the office
 * can remember at the counter. Before this, a student's whole history of
 * prefectship and sport existed only inside certificates already handed out.
 *
 * Grouped by kind rather than shown as one flat list, because that is how the
 * certificate reads them — each group becomes one numbered item on the form.
 */
const KINDS = [
  {
    value: 'LEADERSHIP',
    label: 'Leadership',
    blurb: 'Posts held — class monitor, junior prefect, senior prefect.',
    hasType: true,
    detailLabel: 'Nature of leadership and year',
    detailHint: 'e.g. Junior Prefect – 2021',
  },
  {
    value: 'CO_CURRICULAR',
    label: 'Co-curricular',
    blurb: 'Art, music, dancing, drama and sport.',
    hasType: true,
    detailLabel: 'Level and achievement',
    detailHint: 'e.g. All Island Championship 2021',
  },
  {
    value: 'TALENT',
    label: 'Other talents & abilities',
    blurb: 'Anything else the school wants recorded.',
    hasType: false,
    detailLabel: 'Talent or ability',
    detailHint: null,
  },
  {
    value: 'CONDUCT',
    label: 'Conduct & behaviour',
    blurb: 'Observations on conduct, in the school’s own words.',
    hasType: false,
    detailLabel: 'Observation',
    detailHint: null,
  },
  {
    value: 'HEALTH',
    label: 'Health & weaknesses',
    blurb: 'Conditions identified at a medical examination.',
    hasType: false,
    detailLabel: 'Observation',
    detailHint: null,
  },
];

const EMPTY = {
  id: null,
  kind: 'LEADERSHIP',
  type: '',
  subType: '',
  otherType: '',
  detail: '',
  year: '',
};

export default function AchievementDrawer({ open, student, onClose }) {
  const { can } = useAuth();
  const privilege = can('Achievement');
  const toast = useToast();

  const [rows, setRows] = useState([]);
  const [options, setOptions] = useState({});
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState(EMPTY);
  const [pendingDelete, setPendingDelete] = useState(null);

  const studentId = student?.id;

  const load = useCallback(async () => {
    if (!open || !studentId) return;

    setLoading(true);
    try {
      const [list, picks] = await Promise.all([
        achievements.list(studentId),
        achievements.options(),
      ]);
      setRows(list);
      setOptions(picks);
    } catch (error) {
      toast.error(error.message ?? 'The record could not be loaded.');
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, [open, studentId, toast]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (!open) setForm(EMPTY);
  }, [open]);

  const kind = useMemo(() => KINDS.find((item) => item.value === form.kind) ?? KINDS[0], [form.kind]);

  /**
   * The type picker's contents, which depend on the kind.
   *
   * Served from the API rather than duplicated here, so the list the form
   * offers and the list the server validates against cannot drift.
   */
  const typeOptions = useMemo(() => {
    const source =
      form.kind === 'LEADERSHIP' ? options.leadershipTypes : options.coCurricularTypes;
    return (source ?? []).map((name) => ({ value: name, label: name }));
  }, [form.kind, options]);

  const sportOptions = useMemo(
    () => (options.sports ?? []).map((name) => ({ value: name, label: name })),
    [options],
  );

  const set = (field) => (event) => {
    const value = event?.target ? event.target.value : event;
    setForm((current) => {
      const next = { ...current, [field]: value };
      // Changing the kind or the type clears what the new choice cannot have,
      // so a sport left behind cannot end up printing as "Music (Cricket)".
      if (field === 'kind') {
        return { ...next, type: '', subType: '', otherType: '' };
      }
      if (field === 'type') {
        return { ...next, subType: '', otherType: '' };
      }
      return next;
    });
  };

  const editRow = (row) => {
    setForm({
      id: row.id,
      kind: row.kind,
      type: row.type ?? '',
      subType: row.subType ?? '',
      otherType: row.otherType ?? '',
      detail: row.detail ?? '',
      year: row.year ?? '',
    });
  };

  const submit = async (event) => {
    event.preventDefault();

    if (!form.detail.trim()) {
      toast.error(`${kind.detailLabel} is required.`);
      return;
    }

    const payload = {
      student_id: { id: studentId },
      kind: form.kind,
      type: kind.hasType ? form.type || null : null,
      subType: form.type === 'Sport' ? form.subType || null : null,
      otherType: form.type === 'Other' ? form.otherType || null : null,
      detail: form.detail.trim(),
      year: form.year === '' ? null : Number(form.year),
    };

    setSaving(true);
    try {
      if (form.id) {
        await achievements.update(form.id, payload);
        toast.success('Updated.');
      } else {
        await achievements.create(payload);
        toast.success('Recorded.');
      }
      setForm(EMPTY);
      await load();
    } catch (error) {
      toast.error(error.message ?? 'It could not be saved.');
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    try {
      await achievements.remove(pendingDelete.id);
      toast.success('Removed.');
      if (form.id === pendingDelete.id) setForm(EMPTY);
      await load();
    } catch (error) {
      toast.error(error.message ?? 'It could not be removed.');
    } finally {
      setPendingDelete(null);
    }
  };

  const grouped = useMemo(
    () =>
      KINDS.map((entry) => ({
        ...entry,
        items: rows.filter((row) => row.kind === entry.value),
      })),
    [rows],
  );

  return (
    <>
      <Drawer
        open={open}
        onClose={onClose}
        title={`Conduct & achievements — ${student?.fullname ?? ''}`}
        description="What the last four items of the leaving certificate are drafted from."
        size="lg"
        footer={
          <Button variant="secondary" onClick={onClose}>
            Close
          </Button>
        }
      >
        {loading ? (
          <LoadingPanel label="Loading the record" />
        ) : (
          <>
            {privilege.insert && (
              <form
                onSubmit={submit}
                className="mb-6 rounded-panel bg-slate-50 p-4 ring-1 ring-slate-900/5 dark:bg-slate-800/50 dark:ring-white/10"
              >
                <p className="mb-3 text-sm font-medium text-slate-700 dark:text-slate-200">
                  {form.id ? 'Edit entry' : 'Add an entry'}
                </p>

                <div className="grid gap-3 sm:grid-cols-2">
                  <SelectField
                    label="Kind"
                    options={KINDS.map((item) => ({ value: item.value, label: item.label }))}
                    value={form.kind}
                    onChange={set('kind')}
                    hint={kind.blurb}
                  />

                  {kind.hasType && (
                    <SelectField
                      label="Type"
                      options={typeOptions}
                      placeholder="Select…"
                      value={form.type}
                      onChange={set('type')}
                    />
                  )}

                  {/* Sport is the one type with a second level. */}
                  {kind.hasType && form.type === 'Sport' && (
                    <SelectField
                      label="Sport"
                      options={sportOptions}
                      placeholder="Select…"
                      value={form.subType}
                      onChange={set('subType')}
                    />
                  )}

                  {kind.hasType && form.type === 'Other' && (
                    <TextField
                      label="If other, specify"
                      value={form.otherType}
                      onChange={set('otherType')}
                    />
                  )}

                  <TextField
                    label="Year"
                    type="number"
                    min="1950"
                    max="2100"
                    placeholder="e.g. 2021"
                    value={form.year}
                    onChange={set('year')}
                  />

                  <TextArea
                    label={kind.detailLabel}
                    className="sm:col-span-2"
                    rows={2}
                    hint={kind.detailHint}
                    value={form.detail}
                    onChange={set('detail')}
                  />
                </div>

                <div className="mt-3 flex gap-2">
                  <Button type="submit" loading={saving}>
                    {form.id ? 'Save changes' : 'Add'}
                  </Button>
                  {form.id && (
                    <Button type="button" variant="secondary" onClick={() => setForm(EMPTY)}>
                      Cancel
                    </Button>
                  )}
                </div>
              </form>
            )}

            {rows.length === 0 ? (
              <EmptyState
                title="Nothing recorded yet"
                message="Add prefect appointments, sports, talents and health notes as they happen — the leaving certificate is drafted from them."
              />
            ) : (
              <div className="space-y-5">
                {grouped
                  .filter((group) => group.items.length > 0)
                  .map((group) => (
                    <section key={group.value}>
                      <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                        {group.label}
                      </h3>
                      <ul className="space-y-2">
                        {group.items.map((row) => (
                          <li
                            key={row.id}
                            className="flex items-start justify-between gap-3 rounded-lg border border-slate-200 bg-white p-3 dark:border-slate-700 dark:bg-slate-900"
                          >
                            <div className="min-w-0">
                              <div className="flex flex-wrap items-center gap-2">
                                {row.type && (
                                  <Badge tone="neutral">
                                    {row.type === 'Other' && row.otherType
                                      ? row.otherType
                                      : row.type}
                                    {row.subType ? ` · ${row.subType}` : ''}
                                  </Badge>
                                )}
                                {row.year && (
                                  <span className="text-xs text-slate-500 dark:text-slate-400">
                                    {row.year}
                                  </span>
                                )}
                              </div>
                              <p className="mt-1 whitespace-pre-line text-sm text-slate-700 dark:text-slate-200">
                                {row.detail}
                              </p>
                            </div>

                            <div className="flex shrink-0 gap-1">
                              {privilege.update && (
                                <button
                                  type="button"
                                  onClick={() => editRow(row)}
                                  className="rounded-lg px-2 py-1 text-xs text-slate-500 transition hover:bg-slate-100 hover:text-brand-600 dark:hover:bg-slate-800"
                                >
                                  Edit
                                </button>
                              )}
                              {privilege.delete && (
                                <button
                                  type="button"
                                  onClick={() => setPendingDelete(row)}
                                  className="rounded-lg px-2 py-1 text-xs text-slate-500 transition hover:bg-rose-50 hover:text-rose-600 dark:hover:bg-rose-950/40"
                                >
                                  Remove
                                </button>
                              )}
                            </div>
                          </li>
                        ))}
                      </ul>
                    </section>
                  ))}
              </div>
            )}
          </>
        )}
      </Drawer>

      <ConfirmDialog
        open={!!pendingDelete}
        title="Remove this entry?"
        message="Certificates already issued keep the wording they were printed with — only future drafts change."
        confirmLabel="Remove"
        onConfirm={remove}
        onCancel={() => setPendingDelete(null)}
      />
    </>
  );
}
