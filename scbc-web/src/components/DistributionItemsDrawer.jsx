import { useCallback, useEffect, useState } from 'react';
import { useToast } from '@/context/ToastContext';
import { distributions, lookups } from '@/lib/resources';
import { useResource } from '@/hooks/useResource';

import Drawer from '@/components/ui/Drawer';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import EmptyState from '@/components/ui/EmptyState';
import { LoadingPanel } from '@/components/ui/Spinner';
import { FormSection } from '@/components/ui/PageHeader';
import { SelectField, TextField, Toggle } from '@/components/ui/Field';

const EMPTY = { name: '', code: '', kind: 'UNIFORM', gradeId: '', sortOrder: '', active: true };

/**
 * The things that get handed out — uniform garments and textbooks.
 *
 * These are the columns of the distribution sheet. The school's originals had
 * them printed on: six uniform columns headed JB(S), IB(S), SB(S)… and twelve
 * numbered book columns with the titles written in by hand each year. Holding
 * them as rows means a new garment or a changed book list is typed in here
 * rather than needing a new spreadsheet.
 */
export default function DistributionItemsDrawer({ open, onClose, onChanged }) {
  const toast = useToast();

  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState(EMPTY);
  const [editing, setEditing] = useState(null);
  const [saving, setSaving] = useState(false);
  const [pendingDelete, setPendingDelete] = useState(null);

  const gradeList = useResource(useCallback(() => lookups.grades(), []));

  const load = useCallback(async () => {
    if (!open) return;
    setLoading(true);
    try {
      setItems(await distributions.items());
    } catch (caught) {
      toast.error(caught.message);
    } finally {
      setLoading(false);
    }
  }, [open, toast]);

  useEffect(() => {
    load();
  }, [load]);

  const set = (field) => (event) =>
    setForm((current) => ({ ...current, [field]: event.target.value }));

  const reset = () => {
    setForm(EMPTY);
    setEditing(null);
  };

  const startEdit = (item) => {
    setEditing(item);
    setForm({
      name: item.name ?? '',
      code: item.code ?? '',
      kind: item.kind ?? 'UNIFORM',
      gradeId: item.gradeId ? String(item.gradeId) : '',
      // Position 0 is the first column, not "unset", so this cannot lean on
      // falsiness.
      sortOrder:
        item.sortOrder === null || item.sortOrder === undefined ? '' : String(item.sortOrder),
      active: item.active !== false,
    });
  };

  const submit = async (event) => {
    event.preventDefault();
    if (!form.name.trim()) {
      toast.error('An item name is required.');
      return;
    }

    const payload = {
      name: form.name.trim(),
      code: form.code.trim() || null,
      kind: form.kind,
      gradeId: form.gradeId ? Number(form.gradeId) : null,
      sortOrder: form.sortOrder === '' ? 0 : Number(form.sortOrder),
      active: form.active,
    };

    setSaving(true);
    try {
      if (editing) {
        await distributions.updateItem(editing.id, payload);
        toast.success(`${payload.name} updated.`);
      } else {
        await distributions.createItem(payload);
        toast.success(`${payload.name} added.`);
      }
      reset();
      await load();
      onChanged?.();
    } catch (caught) {
      toast.error(caught.message);
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    try {
      await distributions.removeItem(pendingDelete.id);
      toast.success(`${pendingDelete.name} deleted.`);
      setPendingDelete(null);
      await load();
      onChanged?.();
    } catch (caught) {
      toast.error(caught.message);
      setPendingDelete(null);
    }
  };

  const uniforms = items.filter((item) => item.kind === 'UNIFORM');
  const books = items.filter((item) => item.kind === 'BOOK');

  const gradeName = (id) =>
    gradeList.data.find((grade) => String(grade.id) === String(id))?.name ?? `Grade ${id}`;

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title="Items handed out"
      description="The columns of the distribution sheets. Add a garment or a textbook and it appears on the grid."
      size="lg"
      footer={
        <Button variant="secondary" onClick={onClose}>
          Done
        </Button>
      }
    >
      <form onSubmit={submit} noValidate>
        <FormSection title={editing ? `Edit ${editing.name}` : 'Add an item'} columns={2}>
          <TextField
            label="Name"
            required
            value={form.name}
            onChange={set('name')}
            placeholder="Junior Boys Shirt"
          />
          <TextField
            label="Column heading"
            value={form.code}
            onChange={set('code')}
            placeholder="JB(S)"
            hint="Short form for the sheet. The full name prints beneath it."
          />
          <SelectField
            label="Kind"
            value={form.kind}
            onChange={set('kind')}
            options={[
              { value: 'UNIFORM', label: 'Uniform' },
              { value: 'BOOK', label: 'Book' },
            ]}
          />
          <SelectField
            label="Grade"
            value={form.gradeId}
            onChange={set('gradeId')}
            placeholder="Every grade"
            options={gradeList.data.map((grade) => ({
              value: String(grade.id),
              label: grade.name,
            }))}
            hint="Textbooks are per grade; uniform sizes usually are not."
          />
          <TextField
            label="Column position"
            type="number"
            value={form.sortOrder}
            onChange={set('sortOrder')}
            hint="Lowest prints first."
          />
          <Toggle
            label="In use"
            description="A retired item stays on past sheets but cannot be issued."
            checked={form.active}
            onChange={(next) => setForm((current) => ({ ...current, active: next }))}
          />
        </FormSection>

        <div className="mb-6 flex gap-2">
          <Button type="submit" loading={saving}>
            {editing ? 'Save changes' : 'Add item'}
          </Button>
          {editing && (
            <Button type="button" variant="secondary" onClick={reset}>
              Cancel
            </Button>
          )}
        </div>
      </form>

      {loading ? (
        <LoadingPanel label="Loading items" />
      ) : items.length === 0 ? (
        <EmptyState
          title="Nothing set up yet"
          message="Add the uniform garments and textbooks your school hands out. Each one becomes a column on the distribution sheet."
        />
      ) : (
        [
          { label: 'Uniform', rows: uniforms },
          { label: 'Books', rows: books },
        ]
          .filter((group) => group.rows.length > 0)
          .map((group) => (
            <section key={group.label} className="mb-5">
              <h3 className="mb-2 text-sm font-semibold text-slate-800 dark:text-slate-200">
                {group.label}
              </h3>
              <ul className="divide-y divide-slate-100 dark:divide-slate-800">
                {group.rows.map((item) => (
                  <li key={item.id} className="flex flex-wrap items-center gap-3 py-2.5">
                    <span className="min-w-0 flex-1">
                      <span className="block text-sm font-medium text-slate-800 dark:text-slate-100">
                        {item.name}
                        {item.code && (
                          <span className="ml-2 text-xs font-normal text-slate-400">
                            {item.code}
                          </span>
                        )}
                        {item.active === false && (
                          <Badge tone="neutral" className="ml-2">
                            Retired
                          </Badge>
                        )}
                      </span>
                      <span className="block text-xs text-slate-400 dark:text-slate-500">
                        {item.gradeId ? gradeName(item.gradeId) : 'Every grade'} · position{' '}
                        {item.sortOrder ?? 0}
                      </span>
                    </span>
                    <Button size="sm" variant="secondary" onClick={() => startEdit(item)}>
                      Edit
                    </Button>
                    <Button size="sm" variant="ghost" onClick={() => setPendingDelete(item)}>
                      Delete
                    </Button>
                  </li>
                ))}
              </ul>
            </section>
          ))
      )}

      <ConfirmDialog
        open={!!pendingDelete}
        title="Delete this item?"
        message={`${pendingDelete?.name ?? ''} will be removed. An item already issued to students cannot be deleted — retire it instead, so past sheets keep their columns.`}
        confirmLabel="Delete item"
        onConfirm={handleDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </Drawer>
  );
}
