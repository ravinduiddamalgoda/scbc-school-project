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
        long studentCount) {

    public static ClassroomResponse of(Classroom classroom, long subjectCount, long studentCount) {
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
                studentCount);
    }
}
