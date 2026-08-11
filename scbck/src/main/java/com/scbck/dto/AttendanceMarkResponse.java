package com.scbck.dto;

/**
 * One student's line on the register.
 *
 * @param present true, false, or null for "not marked". The three-way value is
 *                deliberate: treating an unmarked student as present is how a
 *                half-finished register turns into a day of perfect
 *                attendance, and the percentage never recovers.
 */
public record AttendanceMarkResponse(
        Integer studentId,
        String studentNo,
        String name,
        Boolean present) {
}
