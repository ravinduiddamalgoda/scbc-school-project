package com.scbck.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * One day's register for one class, as the marking screen needs it.
 *
 * It is returned whether or not the register has been opened: an unmarked day
 * comes back with the roll listed and every mark null, so the screen shows the
 * same page before and after the first save. {@code id} is null until the day
 * is saved for the first time.
 */
public record AttendanceSheetResponse(
        Integer id,
        LocalDate date,
        NamedRef classroom,
        NamedRef grade,
        NamedRef academicYear,
        NamedRef classTeacher,
        /** True once this day has been saved at least once. */
        boolean marked,
        int present,
        int absent,
        /** On the roll but with no mark recorded - an unfinished register. */
        int unmarked,
        int total,
        List<AttendanceMarkResponse> students) {
}
