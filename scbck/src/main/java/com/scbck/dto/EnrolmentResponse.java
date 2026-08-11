package com.scbck.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.scbck.model.StudentRegistration;

/** A student's placement in a class, with the subjects they take there. */
public record EnrolmentResponse(
        Integer id,
        String regNo,
        LocalDate date,
        BigDecimal totalFee,
        NamedRef student,
        String studentNo,
        NamedRef classroom,
        NamedRef grade,
        NamedRef academicYear,
        NamedRef classTeacher,
        NamedRef status,
        List<ClassroomSubjectResponse> subjects) {

    public static EnrolmentResponse of(StudentRegistration registration, List<ClassroomSubjectResponse> subjects) {
        var student = registration.getStudent_id();
        var classroom = registration.getClassroom_id();
        var grade = classroom == null ? null : classroom.getGrade_id();
        var year = classroom == null ? null : classroom.getAcademic_year_id();
        var teacher = classroom == null ? null : classroom.getEmployee_id();
        var status = registration.getRegistration_status_id();

        return new EnrolmentResponse(
                registration.getId(),
                registration.getReg_no(),
                registration.getDate(),
                registration.getTotal_fee(),
                student == null ? null : NamedRef.of(student.getId(), student.getFullname()),
                student == null ? null : student.getStu_no(),
                classroom == null ? null : NamedRef.of(classroom.getId(), classroom.getName()),
                grade == null ? null : NamedRef.of(grade.getId(), grade.getName()),
                year == null ? null : NamedRef.of(year.getId(), year.getName()),
                teacher == null ? null : NamedRef.of(teacher.getId(), teacher.getFullname()),
                status == null ? null : NamedRef.of(status.getId(), status.getName()),
                subjects);
    }
}
