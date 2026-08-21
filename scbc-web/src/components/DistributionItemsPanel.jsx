import { useState } from 'react';

import SetupPanel from '@/components/ui/SetupPanel';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import EmptyState from '@/components/ui/EmptyState';
import DistributionItemsDrawer from '@/components/DistributionItemsDrawer';

/**
 * The uniform and book items the distribution sheet has a column for.
 *
 * Surfaced here because this is where the school went looking for it: when no
 * items exist the distribution sheet said "add them under Academic setup
 * first", and Academic setup had nothing of the kind — the editor was a button
 * on the distribution screen itself. Rather than only correcting the message,
 * the list is now reachable from both, since one of them is where people
 * expect it and the other is where the work is actually being done.
 *
 * The editing itself is the same drawer either way, against the same endpoints,
 * so there is one list and one place it is defined.
 */
export default function DistributionItemsPanel({ items, loading, privilege, onChanged }) {
  const [open, setOpen] = useState(false);

  const uniforms = items.filter((item) => item.kind === 'UNIFORM');
  const books = items.filter((item) => item.kind === 'BOOK');

  return (
    <>
      <SetupPanel
        title="Uniform & book items"
        description="The columns the distribution sheet records against — set once, then used all year."
        actions={
          privilege.update && (
            <Button variant="secondary" onClick={() => setOpen(true)}>
              Manage items
            </Button>
          )
        }
      >
        {loading ? (
          <div className="p-4 text-sm text-slate-500 dark:text-slate-400">Loading…</div>
        ) : items.length === 0 ? (
          <div className="p-4">
            <EmptyState
              title="No items yet"
              message="Add the uniform pieces and books the school hands out. Until then the distribution sheet has no columns to record against."
            />
          </div>
        ) : (
          <div className="grid gap-4 p-4 sm:grid-cols-2">
            <ItemGroup title="Uniforms" items={uniforms} />
            <ItemGroup title="Books" items={books} />
          </div>
        )}
      </SetupPanel>

      <DistributionItemsDrawer
        open={open}
        onClose={() => setOpen(false)}
        onChanged={onChanged}
      />
    </>
  );
}

function ItemGroup({ title, items }) {
  return (
    <div>
      <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
        {title}
      </h3>
      {items.length === 0 ? (
        <p className="text-sm text-slate-400">None.</p>
      ) : (
        <ul className="flex flex-wrap gap-1.5">
          {items.map((item) => (
            <li key={item.id}>
              <Badge tone={item.active === false ? 'neutral' : 'brand'}>
                {item.name}
                {item.code ? ` (${item.code})` : ''}
              </Badge>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
