package com.scbck.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * One child as their parent sees them.
 *
 * Deliberately narrower than {@code Student}: a parent has no business reading
 * the audit columns, the internal status row or the free-text note staff keep
 * on a record, and sending the entity would hand over all three. What is here
 * is what a parent asks about at the counter.
 */
public record ParentChild(
        Integer studentId,
        String admissionNo,
        String fullname,
        String className,
        String gradeName,
        LocalDate dateOfBirth) {

    /**
     * A child's marks for one term.
     *
     * The class rank is included because the school's own mark sheet prints it
     * and parents are used to seeing it; the rest of the class is not, so a
     * parent learns their own child's position without learning anyone else's.
     */
    public record TermMarks(
            Integer termId,
            String termName,
            String academicYear,
            List<SubjectMark> subjects,
            Integer total,
            Double average,
            Integer rank,
            Integer outOf) {
    }

    public record SubjectMark(
            String subject,
            String categoryName,
            Integer mark,
            /** A, B, C, S, F, or AB when the student was absent. */
            String grade) {
    }
}
