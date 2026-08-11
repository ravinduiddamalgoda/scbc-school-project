package com.scbck.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Records a fee payment.
 *
 * @param enrolmentId the class enrolment this settles. Optional, but supplying
 *                    it is what lets the Fees Details report say which grade
 *                    the money was for - and it stays right after the student
 *                    is promoted, which reading the grade off the student
 *                    record would not.
 */
public record PaymentRequest(
        @NotNull(message = "is required") Integer studentId,
        Integer enrolmentId,
        Integer paymentTypeId,
        @NotNull(message = "is required") @PositiveOrZero(message = "cannot be negative") BigDecimal amountPaid,
        @PositiveOrZero(message = "cannot be negative") BigDecimal amountDue,
        @NotNull(message = "is required") LocalDate paidDate,
        String billNo) {
}
