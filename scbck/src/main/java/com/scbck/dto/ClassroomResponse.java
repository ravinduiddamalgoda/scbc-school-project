package com.scbck.dto;

import com.scbck.model.Classroom;

/**
 * A class as the listing screen needs it: the labels, plus the two counts that
 * would otherwise cost the client one request per row to work out.
 */
public record ClassroomResponse(
        Integer id,
        String name,
        NamedRef grade,
        NamedRef academicYear,
        NamedRef classTeacher,
        String medium,
        long subjectCount,
        long studentCount,

        /**
         * Whether the caller may change this class and its attendance.
         *
         * True for the assigned class teacher, and for anyone holding an
         * overriding role. Sent so the screen can offer what the server will
         * actually accept: letting a teacher mark a register for somebody
         * else's class and refusing it on save is a worse way to explain the
         * rule than not offering it.
         */
        boolean editable) {

    public static ClassroomResponse of(Classroom classroom, long subjectCount, long studentCount,
            boolean editable) {
        return new ClassroomResponse(
                classroom.getId(),
                classroom.getName(),
                classroom.getGrade_id() == null ? null
                        : NamedRef.of(classroom.getGrade_id().getId(), classroom.getGrade_id().getName()),
                classroom.getAcademic_year_id() == null ? null
                        : NamedRef.of(classroom.getAcademic_year_id().getId(),
                                classroom.getAcademic_year_id().getName()),
                classroom.getEmployee_id() == null ? null
                        : NamedRef.of(classroom.getEmployee_id().getId(), classroom.getEmployee_id().getFullname()),
                classroom.getMedium(),
                subjectCount,
                studentCount,
                editable);
    }

    /**
     * For callers that have no notion of who is asking.
     *
     * Defaults to editable, because the only places that use this are ones
     * where the privilege check has already been made against the whole
     * module rather than against one class.
     */
    public static ClassroomResponse of(Classroom classroom, long subjectCount, long studentCount) {
        return of(classroom, subjectCount, studentCount, true);
    }
}
