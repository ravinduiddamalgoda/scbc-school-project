package com.scbck.dto;

import com.scbck.model.ClassroomSubject;

/** A subject on a class timetable, with its teacher and how many take it. */
public record ClassroomSubjectResponse(
        Integer id,
        NamedRef subject,
        String subjectCode,
        NamedRef teacher,
        long studentCount) {

    public static ClassroomSubjectResponse of(ClassroomSubject link, long studentCount) {
        return new ClassroomSubjectResponse(
                link.getId(),
                link.getSubject_detail_id() == null ? null
                        : NamedRef.of(link.getSubject_detail_id().getId(), link.getSubject_detail_id().getName()),
                link.getSubject_detail_id() == null ? null : link.getSubject_detail_id().getCode(),
                link.getEmployee_id() == null ? null
                        : NamedRef.of(link.getEmployee_id().getId(), link.getEmployee_id().getFullname()),
                studentCount);
    }
}
