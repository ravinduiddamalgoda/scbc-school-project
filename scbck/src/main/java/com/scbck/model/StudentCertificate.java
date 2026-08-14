package com.scbck.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A certificate that has been issued to a student.
 *
 * The Ministry's leaving form asks for eighteen things, and only about half of
 * them are facts the school already records - conduct, health observations,
 * co-curricular activities and the reason for leaving are written by the
 * principal at the moment of issue. Those are stored here rather than on the
 * student, because they describe one certificate on one day: a student who
 * leaves, returns and leaves again has two of them, and overwriting the first
 * would rewrite a document already handed to a parent.
 *
 * Storing the issued text is also what makes a reprint a reprint. Regenerating
 * from the student record would quietly produce a different certificate as soon
 * as anything on that record changed, which for a document a family may present
 * years later is the one thing it must not do.
 */
@Entity
@Table(name = "student_certificate")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StudentCertificate {

    /** Ministry "Student Performance Record" handed over when a student leaves. */
    public static final String LEAVING = "LEAVING";

    /** Free-form testimonial addressed "to whom it may concern". */
    public static final String CHARACTER = "CHARACTER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", referencedColumnName = "id")
    @NotNull(message = "is required")
    private Student student_id;

    /** {@link #LEAVING} or {@link #CHARACTER}. */
    @NotNull(message = "is required")
    @Column(name = "type", length = 20)
    private String type;

    @NotNull(message = "is required")
    private LocalDate issued_date;

    /**
     * The student's name as it appeared on the certificate.
     *
     * Snapshotted with everything else: a name corrected on the record later
     * must not change a document already issued.
     */
    @Column(name = "student_name")
    private String studentName;

    @Column(name = "name_with_initials")
    private String nameWithInitials;

    /** Ministry form: the number the student is enrolled under. */
    @Column(name = "admission_no")
    private String admissionNo;

    private LocalDate date_of_admission;

    private LocalDate date_of_leaving;

    @Column(name = "guardian_name")
    private String guardianName;

    @Lob
    @Column(name = "guardian_address")
    private String guardianAddress;

    @Column(name = "religion")
    private String religion;

    @Column(name = "reason_for_leaving")
    private String reasonForLeaving;

    @Column(name = "last_grade_completed")
    private String lastGradeCompleted;

    @Column(name = "medium_of_instruction")
    private String mediumOfInstruction;

    @Lob
    @Column(name = "subjects_studied")
    private String subjectsStudied;

    @Column(name = "conduct")
    private String conduct;

    @Lob
    @Column(name = "health_notes")
    private String healthNotes;

    @Lob
    @Column(name = "co_curricular")
    private String coCurricular;

    @Lob
    @Column(name = "special_talents")
    private String specialTalents;

    /** Character certificate: the examination last sat, e.g. "G.C.E. O/L". */
    @Column(name = "last_exam_passed")
    private String lastExamPassed;

    /**
     * The body of a character certificate.
     *
     * Held as text rather than assembled at print time because the wording is
     * the principal's, and a testimonial regenerated from a template years
     * later would not be the one that was signed.
     */
    @Lob
    @Column(name = "body")
    private String body;

    @Column(name = "principal_name")
    private String principalName;

    private LocalDateTime added_datetime;

    private Integer added_user_id;
}
