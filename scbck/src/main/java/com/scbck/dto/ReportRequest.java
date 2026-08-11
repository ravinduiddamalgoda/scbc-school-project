package com.scbck.dto;

import java.time.YearMonth;

import com.scbck.exception.ApiException;

/**
 * What a report was asked about.
 *
 * The v1 reports all covered a whole year; the attendance register covers one
 * class for one month, and the fee history covers one student. Rather than a
 * signature per report, every report takes this and reads the parts it needs -
 * which is what lets one endpoint, one PDF writer and one screen serve all of
 * them.
 *
 * A report declares which of these it requires in its {@link ReportSummary},
 * and {@code require*} below turns a missing one into a message that says what
 * to pick rather than a null further down.
 */
public record ReportRequest(
        Integer academicYearId,
        Integer classroomId,
        Integer studentId,
        YearMonth month) {

    public static final String ACADEMIC_YEAR = "academicYear";
    public static final String CLASSROOM = "classroom";
    public static final String STUDENT = "student";
    public static final String MONTH = "month";

    public Integer requireClassroomId() {
        if (classroomId == null) {
            throw ApiException.badRequest("Choose a class to run this report for.");
        }
        return classroomId;
    }

    public Integer requireStudentId() {
        if (studentId == null) {
            throw ApiException.badRequest("Choose a student to run this report for.");
        }
        return studentId;
    }

    /** Defaults to the current month, which is the one a register is usually marked for. */
    public YearMonth monthOrCurrent() {
        return month == null ? YearMonth.now() : month;
    }
}
