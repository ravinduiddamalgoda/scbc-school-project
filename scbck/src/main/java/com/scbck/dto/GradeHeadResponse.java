package com.scbck.dto;

import com.scbck.model.Grade;
import com.scbck.model.GradeHead;

/**
 * One line of the Grade Heads screen and report.
 *
 * Every grade appears, assigned or not - {@code id} and {@code head} are null
 * for the ones still waiting. A grade with nobody heading it is the thing the
 * report exists to surface, so hiding those rows would defeat it.
 */
public record GradeHeadResponse(Integer id, NamedRef grade, NamedRef head, String staffNo) {

    public static GradeHeadResponse unassigned(Grade grade) {
        return new GradeHeadResponse(null, NamedRef.of(grade.getId(), grade.getName()), null, null);
    }

    public static GradeHeadResponse of(GradeHead assignment) {
        var employee = assignment.getEmployee_id();
        return new GradeHeadResponse(
                assignment.getId(),
                NamedRef.of(assignment.getGrade_id().getId(), assignment.getGrade_id().getName()),
                NamedRef.of(employee.getId(), employee.getFullname()),
                employee.getEmp_no());
    }
}
