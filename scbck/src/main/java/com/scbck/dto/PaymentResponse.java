package com.scbck.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.scbck.model.Payment;

/** A receipt, with the grade and year it was for already resolved. */
public record PaymentResponse(
        Integer id,
        String billNo,
        BigDecimal amountPaid,
        BigDecimal amountDue,
        BigDecimal balance,
        LocalDate paidDate,
        NamedRef student,
        String studentNo,
        NamedRef grade,
        NamedRef classroom,
        NamedRef academicYear,
        NamedRef paymentType) {

    public static PaymentResponse of(Payment payment) {
        var student = payment.getStudent_id();
        var enrolment = payment.getStudent_registration_id();
        var classroom = enrolment == null ? null : enrolment.getClassroom_id();
        var grade = classroom == null ? null : classroom.getGrade_id();
        var year = classroom == null ? null : classroom.getAcademic_year_id();
        var type = payment.getPayment_type_id();

        return new PaymentResponse(
                payment.getId(),
                payment.getBill_no(),
                payment.getAmount_paid(),
                payment.getAmount_due(),
                payment.getBalance_amount(),
                payment.getPaid_date(),
                student == null ? null : NamedRef.of(student.getId(), student.getFullname()),
                student == null ? null : student.getStu_no(),
                grade == null ? null : NamedRef.of(grade.getId(), grade.getName()),
                classroom == null ? null : NamedRef.of(classroom.getId(), classroom.getName()),
                year == null ? null : NamedRef.of(year.getId(), year.getName()),
                type == null ? null : NamedRef.of(type.getId(), type.getName()));
    }
}
