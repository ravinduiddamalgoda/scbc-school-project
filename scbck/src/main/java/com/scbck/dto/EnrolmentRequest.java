package com.scbck.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotNull;

/**
 * Places a student in a class and records the subjects they take there.
 *
 * The subject ids are {@code classroom_subject} ids, not subject ids: a
 * student can only take a subject their own class is offered, and addressing
 * the timetable line rather than the subject is what makes that impossible to
 * get wrong.
 *
 * @param classroomSubjectIds null leaves the current selection alone; an empty
 *                            list clears it
 */
public record EnrolmentRequest(
        @NotNull(message = "is required") Integer studentId,
        @NotNull(message = "is required") Integer classroomId,
        Integer registrationStatusId,
        LocalDate date,
        BigDecimal totalFee,
        List<Integer> classroomSubjectIds) {
}
