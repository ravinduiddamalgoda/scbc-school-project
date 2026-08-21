package com.scbck.dto;

import java.util.List;

/**
 * One subject's School Based Assessment sheet, merged across both grades.
 *
 * The Department's workbook is one sheet per subject covering two years of
 * assessment - grades 12 and 13 for A/L, 10 and 11 for O/L - with the senior
 * grade's terms printed first. Marks are entered a grade and a term at a time;
 * this is the merge, and it is done on the server so the grid on screen, the
 * totals and the workbook cannot disagree.
 *
 * @param columns the five assessment columns, senior grade first, in the order
 *                they print; each row's marks are positionally aligned to this
 *                list
 */
public record SbaSheet(
        String exam,
        String examLabel,
        Integer examYear,
        Integer subjectId,
        String subjectName,
        Integer subjectCode,
        String medium,

        /** The school's own identifying facts, as the sheet's header prints them. */
        String schoolName,
        String schoolNo,
        String censusNo,
        String zone,

        List<Column> columns,
        List<Row> rows) {

    /**
     * One assessment column: a term of a grade.
     *
     * @param grade 13, 12, 11 or 10
     * @param term  1, 2 or 3
     */
    public record Column(int grade, int term, String gradeLabel, String termLabel) {
    }

    /**
     * One candidate's line.
     *
     * @param marks positionally aligned to {@link SbaSheet#columns()}; a null
     *              entry is a term not yet assessed, which is not the same as a
     *              zero and is not counted in the total
     * @param total the sum of the marks recorded, project mark included
     */
    public record Row(
            int index,
            Integer studentId,
            String admissionNo,
            String nameWithInitials,
            String groupName,
            Integer projectMarks,
            List<Integer> marks,
            int total) {
    }
}
