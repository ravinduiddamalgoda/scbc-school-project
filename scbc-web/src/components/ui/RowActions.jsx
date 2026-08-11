/**
 * The view / edit / delete controls shown at the end of each table row.
 *
 * Buttons are hidden when the signed-in user lacks the matching privilege, so
 * the row never offers an action the server would reject.
 */
const ICONS = {
  view: 'M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z',
  edit: 'M12 20h9M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z',
  delete: 'M3 6h18M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2m3 0v14a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V6',
};

function IconButton({ kind, label, onClick, tone = 'neutral' }) {
  return (
    <button
      type="button"
      onClick={onClick}
      title={label}
      aria-label={label}
      className={[
        'rounded-lg p-2 transition',
        tone === 'danger'
          ? 'text-slate-400 hover:bg-negative-50 hover:text-negative-600 dark:hover:bg-negative-900/30 dark:hover:text-negative-500'
          : 'text-slate-400 hover:bg-slate-100 hover:text-brand-600 dark:hover:bg-slate-800 dark:hover:text-brand-400',
      ].join(' ')}
    >
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.9"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="size-4"
        aria-hidden="true"
      >
        <path d={ICONS[kind]} />
        {kind === 'view' && <circle cx="12" cy="12" r="3" />}
      </svg>
    </button>
  );
}

export default function RowActions({ onView, onEdit, onDelete, canEdit = true, canDelete = true }) {
  return (
    <>
      {onView && <IconButton kind="view" label="View details" onClick={onView} />}
      {onEdit && canEdit && <IconButton kind="edit" label="Edit" onClick={onEdit} />}
      {onDelete && canDelete && (
        <IconButton kind="delete" label="Delete" tone="danger" onClick={onDelete} />
      )}
    </>
  );
}
