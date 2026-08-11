package com.scbck.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotNull;

/**
 * Saves one day's register for one class.
 *
 * The whole day is written at once rather than mark by mark, because that is
 * how a register is filled in - a teacher goes down the roll and then saves.
 * Students left out of {@code marks} have their mark cleared, so correcting a
 * register never leaves a stale row behind.
 */
public record AttendanceSaveRequest(
        @NotNull(message = "is required") Integer classroomId,
        @NotNull(message = "is required") LocalDate date,
        @NotNull(message = "is required") List<Mark> marks) {

    public record Mark(
            @NotNull(message = "is required") Integer studentId,
            @NotNull(message = "is required") Boolean present) {
    }
}
