package com.scbck.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * What one student owes for one year, and what they have paid against it.
 *
 * The payment form used to ask the clerk to type the amount due on every
 * receipt. This is that figure worked out instead: the grade's fee for the
 * year, less everything already receipted.
 */
public record FeePosition(
        Integer studentId,
        String admissionNo,
        String studentName,
        NamedRef grade,
        NamedRef classroom,
        NamedRef academicYear,

        /**
         * The grade's fee for the year, or null when the school has not set
         * one - in which case the form falls back to being typed, and says so
         * rather than defaulting to zero.
         */
        BigDecimal annualFee,
        String feeNote,

        BigDecimal totalPaid,
        /** Fee less paid. Negative when the family has overpaid. */
        BigDecimal balance,

        /** The receipts making up {@code totalPaid}, newest first. */
        List<PaymentResponse> payments) {
}
