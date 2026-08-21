package com.scbck.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * One student's attendance over a period, broken down by week.
 *
 * The attendance module could previously only be read a class at a time: to
 * answer "how has this child been attending" somebody opened thirty register
 * pages and counted. This is that question asked directly, and it is also what
 * decides which of the three letters the school may send - so the thresholds
 * are computed here rather than in the browser, where a letter could be
 * produced for a student who does not meet them.
 */
public record StudentAttendanceSummary(
        Integer studentId,
        String admissionNo,
        String studentName,
        /** The class the student is enrolled in, e.g. "Grade 11 B". */
        String className,
        String guardianName,
        LocalDate from,
        LocalDate to,

        /** Days a register was marked for the student's class in the period. */
        int daysConducted,
        int daysPresent,
        int daysAbsent,
        /** Present as a percentage of conducted, rounded to one decimal. */
        double attendancePercentage,

        List<Week> weeks,

        /**
         * The unbroken run of absences ending at the most recent marked day.
         *
         * Zero when the student's last marked day was a present. This is the
         * number the twenty- and forty-day letters are about: the Ministry's
         * circular is about continuous absence, not about a total.
         */
        int consecutiveAbsentDays,
        LocalDate absentSince,
        LocalDate lastMarkedDate,

        /**
         * Which letters the record justifies - some of {@code WEEK},
         * {@code TWENTY_DAY}, {@code FORTY_DAY}.
         *
         * The screen enables its buttons from this rather than deciding for
         * itself, and the endpoint that renders a letter re-checks the same
         * rule, so a letter cannot be produced by typing its URL.
         */
        List<String> availableLetters) {

    /**
     * One week of the period.
     *
     * Weeks run Monday to Sunday and are numbered within the period rather than
     * within the year, which is how the school's own monthly register sheet
     * labels them: Week 1 to Week 5 down the side of the month.
     */
    public record Week(
            int number,
            LocalDate from,
            LocalDate to,
            int conducted,
            int present,
            int absent) {
    }
}
