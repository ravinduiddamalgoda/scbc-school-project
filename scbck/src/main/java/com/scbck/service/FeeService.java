package com.scbck.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scbck.dto.FeePosition;
import com.scbck.dto.NamedRef;
import com.scbck.dto.PaymentResponse;
import com.scbck.exception.ApiException;
import com.scbck.model.AcademicYear;
import com.scbck.model.Classroom;
import com.scbck.model.FeeStructure;
import com.scbck.model.Payment;
import com.scbck.model.Student;
import com.scbck.model.StudentRegistration;
import com.scbck.repository.AcademicYearDao;
import com.scbck.repository.FeeStructureDao;
import com.scbck.repository.PaymentDao;
import com.scbck.repository.StudentDao;
import com.scbck.repository.StudentRegistrationDao;

/**
 * What a student owes, and what they have paid against it.
 *
 * The payment screen used to ask the clerk for the amount due on every single
 * receipt, so the same grade's fee was entered by hand hundreds of times a year
 * and disagreed with itself often enough that the outstanding-balance column
 * could not be trusted. The figure now comes from {@link FeeStructure}, and
 * this is the one place that reads it.
 */
@Service
public class FeeService {

    private final StudentDao studentDao;
    private final PaymentDao paymentDao;
    private final FeeStructureDao feeStructureDao;
    private final StudentRegistrationDao registrationDao;
    private final AcademicYearDao academicYearDao;

    public FeeService(StudentDao studentDao, PaymentDao paymentDao, FeeStructureDao feeStructureDao,
            StudentRegistrationDao registrationDao, AcademicYearDao academicYearDao) {
        this.studentDao = studentDao;
        this.paymentDao = paymentDao;
        this.feeStructureDao = feeStructureDao;
        this.registrationDao = registrationDao;
        this.academicYearDao = academicYearDao;
    }

    /**
     * A student's fee position for one year.
     *
     * @param academicYearId the year to report on, or null for the current one
     */
    @Transactional(readOnly = true)
    public FeePosition positionOf(Integer studentId, Integer academicYearId) {
        Student student = studentDao.findById(studentId)
                .orElseThrow(() -> ApiException.notFound("Student " + studentId + " does not exist."));

        AcademicYear year = resolveYear(academicYearId);
        StudentRegistration enrolment = enrolmentIn(studentId, year);
        Classroom classroom = enrolment == null ? null : enrolment.getClassroom_id();

        // The grade comes from the enrolment when there is one, because that is
        // the grade the money is for; the student's own grade is only a
        // fallback for a student not yet enrolled anywhere this year.
        var grade = classroom != null && classroom.getGrade_id() != null
                ? classroom.getGrade_id()
                : student.getGrade_id();

        FeeStructure structure = grade == null || year == null
                ? null
                : feeStructureDao.find(year.getId(), grade.getId()).orElse(null);

        List<Payment> all = paymentDao.listByStudent(studentId);
        List<Payment> forYear = all.stream()
                .filter(payment -> matchesYear(payment, year))
                .toList();

        BigDecimal paid = forYear.stream()
                .map(Payment::getAmount_paid)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal fee = structure == null ? null : structure.getAnnualFee();

        return new FeePosition(
                student.getId(),
                student.getStu_no(),
                student.getFullname(),
                grade == null ? null : NamedRef.of(grade.getId(), grade.getName()),
                classroom == null ? null : NamedRef.of(classroom.getId(), classroom.getName()),
                year == null ? null : NamedRef.of(year.getId(), year.getName()),
                fee,
                structure == null ? null : structure.getNote(),
                paid,
                fee == null ? null : fee.subtract(paid),
                // The history is deliberately every receipt, not only this
                // year's: "print the payment history of a student" is the whole
                // record, which is what a parent asks for at the counter.
                all.stream()
                        .sorted((left, right) -> right.getPaid_date().compareTo(left.getPaid_date()))
                        .map(PaymentResponse::of)
                        .toList());
    }

    /** The fee table for one year, for the Academic setup screen. */
    @Transactional(readOnly = true)
    public List<FeeStructure> structuresFor(Integer academicYearId) {
        AcademicYear year = resolveYear(academicYearId);
        return year == null ? List.of() : feeStructureDao.listForYear(year.getId());
    }

    // -------------------------------------------------------------------------

    private AcademicYear resolveYear(Integer academicYearId) {
        if (academicYearId != null) {
            return academicYearDao.findById(academicYearId)
                    .orElseThrow(() -> ApiException
                            .notFound("Academic year " + academicYearId + " does not exist."));
        }
        List<AcademicYear> current = academicYearDao.listCurrent();
        return current.isEmpty() ? null : current.get(0);
    }

    private StudentRegistration enrolmentIn(Integer studentId, AcademicYear year) {
        if (year == null) {
            return null;
        }
        return registrationDao.listByStudent(studentId).stream()
                .filter(registration -> {
                    Classroom classroom = registration.getClassroom_id();
                    return classroom != null && classroom.getAcademic_year_id() != null
                            && classroom.getAcademic_year_id().getId().equals(year.getId());
                })
                .findFirst()
                .orElse(null);
    }

    /**
     * Whether a receipt counts towards this year's total.
     *
     * A receipt attached to an enrolment belongs to that enrolment's year. One
     * with no enrolment - an ad-hoc receipt - counts towards the year whose
     * dates contain it, so money taken at the counter without a class being
     * named still lands in the right year rather than in none.
     */
    private boolean matchesYear(Payment payment, AcademicYear year) {
        if (year == null) {
            return true;
        }

        StudentRegistration enrolment = payment.getStudent_registration_id();
        if (enrolment != null && enrolment.getClassroom_id() != null
                && enrolment.getClassroom_id().getAcademic_year_id() != null) {
            return enrolment.getClassroom_id().getAcademic_year_id().getId().equals(year.getId());
        }

        if (payment.getPaid_date() == null
                || year.getStart_date() == null || year.getEnd_date() == null) {
            return false;
        }
        return !payment.getPaid_date().isBefore(year.getStart_date())
                && !payment.getPaid_date().isAfter(year.getEnd_date());
    }
}
