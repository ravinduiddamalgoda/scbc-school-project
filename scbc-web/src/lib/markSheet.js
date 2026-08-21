/**
 * The mark sheet's arithmetic, mirrored for the entry grid.
 *
 * The server is still the authority: saving returns a recalculated sheet and
 * the screen replaces its own figures with it. What this adds is the answer
 * *while the teacher is typing*, which the school asked for — a total that only
 * appears after saving is a total nobody can check their entry against.
 *
 * These rules are deliberately a mirror of MarkSheetService and GradeScale on
 * the server, and the two must not drift. Anything changed here has to be
 * changed there:
 *
 *   total     sum of the marks recorded; an absence contributes nothing
 *   recorded  cells holding a mark *or* an absence — an unmarked subject is
 *             neither, and must not drag the average down
 *   average   total / recorded, to one decimal; null when nothing is recorded
 *   rank      competition rank on average, ties sharing a place, unranked
 *             while a student has no average at all
 */

/** A≥75, B≥65, C≥55, S≥35, else F. "AB" absent, "-" not taken. */
export function letterFor(marks, absent) {
  if (absent) return 'AB';
  if (marks === null || marks === undefined) return '-';
  if (marks >= 75) return 'A';
  if (marks >= 65) return 'B';
  if (marks >= 55) return 'C';
  if (marks >= 35) return 'S';
  return 'F';
}

/**
 * Reads one cell as the teacher currently has it.
 *
 * `draft` holds raw strings straight from the inputs, so this is where "AB",
 * an empty box and a half-typed number are turned into the same shape the
 * server works in.
 */
function readCell(cell, draft) {
  const enrolled = cell.enrolled;
  if (!enrolled) return { enrolled: false, marks: null, absent: false };

  if (cell.studentSubjectId in draft) {
    const raw = String(draft[cell.studentSubjectId]).trim();
    if (raw === '') return { enrolled: true, marks: null, absent: false };
    if (raw.toUpperCase() === 'AB') return { enrolled: true, marks: null, absent: true };

    const numeric = Number(raw);
    // A value that is not a valid mark is treated as not yet entered rather
    // than as a zero, so a mistyped digit cannot momentarily show the class a
    // wrong average while it is being corrected.
    if (!Number.isInteger(numeric) || numeric < 0 || numeric > 100) {
      return { enrolled: true, marks: null, absent: false };
    }
    return { enrolled: true, marks: numeric, absent: false };
  }

  return { enrolled: true, marks: cell.marks ?? null, absent: !!cell.absent };
}

/** One student's total, average and per-cell letters, as currently entered. */
export function computeRow(row, draft) {
  let total = 0;
  let recorded = 0;

  const cells = row.cells.map((cell) => {
    const read = readCell(cell, draft);
    if (!read.enrolled) return { ...cell, grade: '-' };

    if (read.marks !== null || read.absent) recorded += 1;
    if (read.marks !== null && !read.absent) total += read.marks;

    return { ...cell, marks: read.marks, absent: read.absent, grade: letterFor(read.marks, read.absent) };
  });

  const average = recorded === 0 ? null : Math.round((total / recorded) * 10) / 10;

  return { ...row, cells, total, average };
}

/**
 * The whole sheet as currently entered, ranks included.
 *
 * Ranks have to be worked out across every row at once — one student's mark
 * changes everybody's position — which is why this recomputes the sheet rather
 * than each row on its own.
 */
export function computeSheet(sheet, draft) {
  if (!sheet) return sheet;

  const rows = sheet.rows.map((row) => computeRow(row, draft));

  const ordered = [...rows]
    .filter((row) => row.average !== null)
    .sort((left, right) => right.average - left.average);

  const rankByIndex = new Map();
  let previous = null;
  let rank = 0;
  let seen = 0;

  for (const row of ordered) {
    seen += 1;
    if (previous === null || previous !== row.average) {
      rank = seen;
      previous = row.average;
    }
    rankByIndex.set(row.index, rank);
  }

  return {
    ...sheet,
    rows: rows.map((row) => ({
      ...row,
      rank: rankByIndex.get(row.index) ?? null,
      highlight: row.average !== null && row.average >= sheet.highlightAverageFrom,
    })),
  };
}
