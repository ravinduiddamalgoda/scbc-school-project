package com.scbck.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/**
 * What the certificate form submits.
 *
 * Deliberately a flat payload rather than the entity. The endpoint used to
 * accept a {@code StudentCertificate} directly, which meant the browser posted
 * back the whole draft it had been given - including a nested student, their
 * guardian, their class and their status - as detached objects for Hibernate to
 * make sense of on the way in. That is how a plain insert ended up reported as
 * "the record conflicts with existing data": the conflict was never in the
 * certificate.
 *
 * Only {@code studentId} is trusted for identity; everything else is the text
 * to print, stored exactly as sent so a reprint is the document that was
 * signed.
 */
public record CertificateRequest(
        @NotNull(message = "is required") Integer studentId,
        @NotNull(message = "is required") String type,
        LocalDate issuedDate,

        String studentName,
        String nameWithInitials,
        String admissionNo,
        LocalDate dateOfAdmission,
        LocalDate dateOfLeaving,
        String guardianName,
        String guardianAddress,
        String religion,
        String reasonForLeaving,
        String lastGradeCompleted,
        String mediumOfInstruction,
        String subjectsStudied,
        String conduct,
        String healthNotes,
        String coCurricular,
        String specialTalents,
        String lastExamPassed,
        String body,
        String principalName) {
}
