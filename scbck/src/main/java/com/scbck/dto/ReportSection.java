package com.scbck.dto;

import java.util.List;

/**
 * One headed block within a report - the grade bands the source spreadsheets
 * lay out side by side ("Grade 1 to Grade 5", "Grade 6 to Grade 9", ...).
 *
 * Splitting by band is what keeps the subject matrices printable: a band only
 * carries the subjects its own grades are taught, so the primary block is ten
 * columns wide instead of the thirty it would need if every A/L subject had to
 * appear in it too.
 *
 * @param footer a totals row, or null when the report has nothing to total
 */
public record ReportSection(
        String title,
        String subtitle,
        List<ReportColumn> columns,
        List<List<String>> rows,
        List<String> footer) {
}
