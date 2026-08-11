package com.scbck.dto;

import jakarta.validation.constraints.NotNull;

/**
 * One line of a class timetable: the subject, and who teaches it.
 *
 * @param teacherId optional - the subject can be on the timetable before the
 *                  staffing is settled, and the Subject Wise Teachers report
 *                  counts only the ones that are filled in
 */
public record ClassroomSubjectRequest(
        @NotNull(message = "is required") Integer subjectId,
        Integer teacherId) {
}
