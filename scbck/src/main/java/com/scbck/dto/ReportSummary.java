package com.scbck.dto;

import java.util.List;

/**
 * An entry in the report catalogue.
 *
 * The client builds its report menu - and the parameter controls each report
 * needs - from this rather than hard-coding them, so a report added on the
 * server appears in the UI, with the right inputs, without a client release.
 *
 * @param parameters names from {@link ReportRequest#ACADEMIC_YEAR} and friends
 */
public record ReportSummary(String key, String title, String description, List<String> parameters) {

    /** Every report is scoped to an academic year; most need nothing else. */
    public static ReportSummary yearly(String key, String title, String description) {
        return new ReportSummary(key, title, description, List.of(ReportRequest.ACADEMIC_YEAR));
    }
}
