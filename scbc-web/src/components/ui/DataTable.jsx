import { useMemo, useState } from 'react';
import Button from './Button';
import EmptyState from './EmptyState';
import { LoadingPanel } from './Spinner';

const PAGE_SIZES = [10, 25, 50, 100];

/**
 * Sortable, searchable, paginated table.
 *
 * Replaces both DataTables and the hand-rolled fillDataIntoTable() helper. The
 * old client fetched every row and rendered all of them at once; this keeps the
 * same single-fetch model but only ever puts one page in the DOM.
 *
 * @param columns - [{ key, header, render?, sortValue?, align?, width?, sortable? }]
 * @param rows    - the full data set
 * @param actions - optional (row) => ReactNode rendered in a trailing column
 */
export default function DataTable({
  columns,
  rows,
  loading = false,
  actions,
  searchable = true,
  searchKeys,
  searchPlaceholder = 'Search…',
  emptyTitle = 'Nothing to show',
  emptyMessage = 'No records have been added yet.',
  initialSort,
  rowKey = (row) => row.id,
  toolbar,
}) {
  const [query, setQuery] = useState('');
  const [sort, setSort] = useState(initialSort ?? null); // { key, direction }
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  /** Text used for searching a row: explicit keys, else every rendered column. */
  const searchText = useMemo(() => {
    const cache = new WeakMap();
    return (row) => {
      if (cache.has(row)) return cache.get(row);

      const parts = searchKeys
        ? searchKeys.map((key) => row[key])
        : columns.map((column) =>
            column.sortValue ? column.sortValue(row) : row[column.key],
          );

      const text = parts
        .filter((part) => part !== null && part !== undefined)
        .join(' ')
        .toLowerCase();

      cache.set(row, text);
      return text;
    };
  }, [columns, searchKeys]);

  const filtered = useMemo(() => {
    const trimmed = query.trim().toLowerCase();
    if (!trimmed) return rows;
    return rows.filter((row) => searchText(row).includes(trimmed));
  }, [rows, query, searchText]);

  const sorted = useMemo(() => {
    if (!sort) return filtered;

    const column = columns.find((candidate) => candidate.key === sort.key);
    if (!column) return filtered;

    const valueOf = column.sortValue ?? ((row) => row[column.key]);
    const direction = sort.direction === 'asc' ? 1 : -1;

    // Copy first: Array.prototype.sort mutates, and rows is owned by the caller.
    return [...filtered].sort((a, b) => {
      const left = valueOf(a);
      const right = valueOf(b);

      if (left === right) return 0;
      if (left === null || left === undefined) return 1;
      if (right === null || right === undefined) return -1;

      if (typeof left === 'number' && typeof right === 'number') {
        return (left - right) * direction;
      }

      return String(left).localeCompare(String(right), undefined, { numeric: true }) * direction;
    });
  }, [filtered, sort, columns]);

  const totalPages = Math.max(1, Math.ceil(sorted.length / pageSize));
  const currentPage = Math.min(page, totalPages);
  const pageRows = sorted.slice((currentPage - 1) * pageSize, currentPage * pageSize);

  const toggleSort = (key) => {
    setPage(1);
    setSort((current) => {
      if (current?.key !== key) return { key, direction: 'asc' };
      if (current.direction === 'asc') return { key, direction: 'desc' };
      return null; // Third click clears the sort.
    });
  };

  const onSearch = (value) => {
    setQuery(value);
    setPage(1);
  };

  return (
    <div className="overflow-hidden rounded-panel bg-white shadow-panel ring-1 ring-slate-900/5 dark:bg-slate-900 dark:ring-white/10">
      {(searchable || toolbar) && (
        <div className="flex flex-col gap-3 border-b border-slate-200 p-4 sm:flex-row sm:items-center sm:justify-between dark:border-slate-800">
          {searchable ? (
            <div className="relative w-full sm:max-w-xs">
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400"
                aria-hidden="true"
              >
                <circle cx="11" cy="11" r="7" />
                <path d="m20 20-3.5-3.5" />
              </svg>

              <input
                type="search"
                value={query}
                onChange={(event) => onSearch(event.target.value)}
                placeholder={searchPlaceholder}
                aria-label={searchPlaceholder}
                className="w-full rounded-lg border border-slate-300 bg-white py-2 pl-9 pr-3 text-sm text-slate-800 shadow-sm transition placeholder:text-slate-400 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
              />
            </div>
          ) : (
            <span />
          )}

          {toolbar}
        </div>
      )}

      {loading ? (
        <LoadingPanel />
      ) : sorted.length === 0 ? (
        <EmptyState
          title={query ? 'No matches' : emptyTitle}
          message={
            query ? `Nothing matches “${query.trim()}”. Try a different search.` : emptyMessage
          }
        />
      ) : (
        <>
          <div className="scroll-x">
            <table className="w-full min-w-max border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-950/50">
                  <th
                    scope="col"
                    className="w-12 px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-400"
                  >
                    #
                  </th>

                  {columns.map((column) => {
                    const isSorted = sort?.key === column.key;
                    const sortable = column.sortable !== false;

                    return (
                      <th
                        key={column.key}
                        scope="col"
                        style={column.width ? { width: column.width } : undefined}
                        aria-sort={
                          isSorted
                            ? sort.direction === 'asc'
                              ? 'ascending'
                              : 'descending'
                            : 'none'
                        }
                        className={[
                          'px-4 py-3 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400',
                          column.align === 'center' ? 'text-center' : 'text-left',
                        ].join(' ')}
                      >
                        {sortable ? (
                          <button
                            type="button"
                            onClick={() => toggleSort(column.key)}
                            className={[
                              'inline-flex items-center gap-1.5 rounded transition hover:text-slate-800 dark:hover:text-slate-200',
                              isSorted ? 'text-brand-600 dark:text-brand-400' : '',
                            ].join(' ')}
                          >
                            {column.header}
                            <svg
                              viewBox="0 0 24 24"
                              fill="none"
                              stroke="currentColor"
                              strokeWidth="2.4"
                              strokeLinecap="round"
                              strokeLinejoin="round"
                              className={`size-3 transition ${isSorted ? 'opacity-100' : 'opacity-30'}`}
                              aria-hidden="true"
                            >
                              {isSorted && sort.direction === 'desc' ? (
                                <path d="m6 9 6 6 6-6" />
                              ) : (
                                <path d="m6 15 6-6 6 6" />
                              )}
                            </svg>
                          </button>
                        ) : (
                          column.header
                        )}
                      </th>
                    );
                  })}

                  {actions && (
                    <th
                      scope="col"
                      className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400"
                    >
                      Actions
                    </th>
                  )}
                </tr>
              </thead>

              <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                {pageRows.map((row, index) => (
                  <tr
                    key={rowKey(row)}
                    className="transition hover:bg-slate-50 dark:hover:bg-slate-800/50"
                  >
                    <td className="px-4 py-3 text-xs font-medium tabular-nums text-slate-400">
                      {(currentPage - 1) * pageSize + index + 1}
                    </td>

                    {columns.map((column) => (
                      <td
                        key={column.key}
                        className={[
                          'px-4 py-3 text-slate-700 dark:text-slate-300',
                          column.align === 'center' ? 'text-center' : '',
                        ].join(' ')}
                      >
                        {column.render ? column.render(row) : (row[column.key] ?? '—')}
                      </td>
                    ))}

                    {actions && (
                      <td className="px-4 py-3">
                        <div className="flex items-center justify-end gap-1">{actions(row)}</div>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="flex flex-col gap-3 border-t border-slate-200 px-4 py-3 sm:flex-row sm:items-center sm:justify-between dark:border-slate-800">
            <div className="flex items-center gap-3 text-xs text-slate-500 dark:text-slate-400">
              <span>
                Showing{' '}
                <span className="font-semibold tabular-nums text-slate-700 dark:text-slate-200">
                  {(currentPage - 1) * pageSize + 1}–
                  {Math.min(currentPage * pageSize, sorted.length)}
                </span>{' '}
                of{' '}
                <span className="font-semibold tabular-nums text-slate-700 dark:text-slate-200">
                  {sorted.length}
                </span>
              </span>

              <label className="flex items-center gap-1.5">
                <span className="sr-only">Rows per page</span>
                <select
                  value={pageSize}
                  onChange={(event) => {
                    setPageSize(Number(event.target.value));
                    setPage(1);
                  }}
                  className="rounded-md border border-slate-300 bg-white py-1 pl-2 pr-6 text-xs text-slate-600 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-300"
                >
                  {PAGE_SIZES.map((size) => (
                    <option key={size} value={size}>
                      {size} / page
                    </option>
                  ))}
                </select>
              </label>
            </div>

            <div className="flex items-center gap-2">
              <Button
                size="sm"
                variant="secondary"
                onClick={() => setPage((current) => Math.max(1, current - 1))}
                disabled={currentPage === 1}
              >
                Previous
              </Button>
              <span className="px-1 text-xs font-medium tabular-nums text-slate-500 dark:text-slate-400">
                {currentPage} / {totalPages}
              </span>
              <Button
                size="sm"
                variant="secondary"
                onClick={() => setPage((current) => Math.min(totalPages, current + 1))}
                disabled={currentPage === totalPages}
              >
                Next
              </Button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
