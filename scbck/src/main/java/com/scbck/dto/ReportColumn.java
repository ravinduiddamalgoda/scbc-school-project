package com.scbck.dto;

/**
 * A report column heading plus the hints both renderers need.
 *
 * @param align  "left", "center" or "right"
 * @param weight relative width; the PDF writer turns these into point widths
 *               and the browser into a flex basis, so a 30-column subject
 *               matrix stays readable in both
 */
public record ReportColumn(String header, String align, float weight) {

    public static ReportColumn text(String header) {
        return new ReportColumn(header, "left", 2f);
    }

    public static ReportColumn wide(String header) {
        return new ReportColumn(header, "left", 4f);
    }

    public static ReportColumn number(String header) {
        return new ReportColumn(header, "center", 1f);
    }
}
