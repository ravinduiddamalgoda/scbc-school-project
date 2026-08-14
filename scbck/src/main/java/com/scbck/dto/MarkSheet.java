package com.scbck.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One class's marks for one term, calculated.
 *
 * The same object serves three consumers - the entry grid, the on-screen sheet
 * and both exporters - so that a mark a teacher sees on screen, the average in
 * the PDF and the rank in the workbook cannot disagree. The school's existing
 * workbook recalculated on open, which is why two printouts of the same term
 * could differ once a formula had been dragged over the wrong range.
 *
 * Everything derived - totals, averages, ranks, letters, the summary block - is
 * computed once here rather than left to the renderer.
 *
 * @param subjects  every subject column, flattened in print order; each row's
 *                  cells are positionally aligned to this list
 * @param highlightAverageFrom the average at or above which a row is emphasised
 */
public record MarkSheet(
        Integer classroomId,
        String className,
        String gradeName,
        String medium,
        String classTeacher,
        Integer termId,
        String termName,
        String academicYear,
        LocalDateTime generatedAt,
        List<Category> categories,
        List<Subject> subjects,
        List<Row> rows,
        List<SubjectSummary> summary,
        double highlightAverageFrom) {

    /**
     * A column band - "Core", "Category 2".
     *
     * @param span how many subject columns the band covers, so a renderer can
     *             merge the heading across them without recounting
     */
    public record Category(Integer id, String name, int span) {
    }

    public record Subject(
            Integer subjectId,
            Integer classroomSubjectId,
            String name,
            /** Short heading used when the full name will not fit. */
            String code,
            String categoryName,
            /** The subject teacher, or null when the timetable has none yet. */
            String teacher) {
    }

    /**
     * One student's line.
     *
     * @param index      position on the sheet, 1-based, as the "Index Number"
     *                   column of the workbook
     * @param total      sum of the marks recorded; an absence adds nothing
     * @param average    total over the number of subjects taken, or null when
     *                   the student takes none
     * @param rank       competition rank on average, ties sharing a place
     * @param highlight  whether this row met {@link #highlightAverageFrom}
     */
    public record Row(
            int index,
            Integer registrationId,
            Integer studentId,
            String admissionNo,
            String studentName,
            List<Cell> cells,
            int total,
            Double average,
            Integer rank,
            List<LetterCount> gradeCounts,
            boolean highlight) {
    }

    /**
     * One student's mark in one subject.
     *
     * @param studentSubjectId the enrolment line to write a mark against, or
     *                         null when the student does not take the subject -
     *                         which is what the entry grid uses to decide
     *                         whether the cell is editable at all
     * @param grade            the letter, "AB" when absent, "-" when not taken
     */
    public record Cell(
            Integer studentSubjectId,
            Integer marks,
            boolean absent,
            boolean enrolled,
            String grade) {
    }

    public record LetterCount(String letter, int count) {
    }

    /**
     * The block the workbook prints under the roster: how many of each letter a
     * subject awarded, and how many marks fell in each band.
     */
    public record SubjectSummary(
            Integer subjectId,
            String subjectName,
            List<LetterCount> letterCounts,
            List<BandCount> bandCounts,
            int absent,
            /** Marks recorded, absences included - the column's "Total" row. */
            int recorded) {
    }

    public record BandCount(String label, int count) {
    }
}
