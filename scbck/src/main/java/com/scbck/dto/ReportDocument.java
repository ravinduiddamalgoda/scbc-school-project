package com.scbck.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A finished report, in the one shape every report in the system takes.
 *
 * Reports are tabular by nature, so rather than a bespoke payload per report
 * they all reduce to sections of headed rows. Two consequences pay for the
 * indirection: the PDF writer renders any report without knowing which one it
 * is, and the client renders any report without a matching component. Adding a
 * fifth report means adding one method to ReportService and nothing else.
 *
 * Cells are pre-formatted strings. Formatting a count is the report's decision,
 * not the renderer's, and it keeps the PDF and the on-screen table identical.
 */
public record ReportDocument(
        String key,
        String title,
        String description,
        String academicYear,
        LocalDateTime generatedAt,
        /** "landscape" for the wide subject matrices, "portrait" otherwise. */
        String orientation,
        List<ReportSection> sections) {

    /** A report with no data at all still renders - it just says so. */
    public boolean isEmpty() {
        return sections.stream().allMatch(section -> section.rows().isEmpty());
    }
}
