package com.scbck.service;

import static com.scbck.service.ReportLayout.document;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.scbck.dto.ReportColumn;
import com.scbck.dto.ReportDocument;
import com.scbck.dto.ReportRequest;
import com.scbck.dto.ReportSection;
import com.scbck.exception.ApiException;
import com.scbck.model.AcademicYear;
import com.scbck.model.Payment;
import com.scbck.model.Student;
import com.scbck.model.StudentRegistration;
import com.scbck.repository.PaymentDao;
import com.scbck.repository.StudentDao;
import com.scbck.repository.StudentRegistrationDao;

/**
 * One student's payment history, grade by grade.
 *
 * The grade on each line comes from the enrolment the payment settled, not
 * from the student's current grade - otherwise every historical receipt would
 * be relabelled the moment the student was promoted, and the report would say
 * they had paid Grade 7 fees eleven times.
 *
 * Deliberately not scoped to one academic year: a fee history is only useful
 * read from admission to today, so it spans every year the student has been
 * here.
 */
@Service
public class FeeReports {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final PaymentDao paymentDao;
    private final StudentDao studentDao;
    private final StudentRegistrationDao registrationDao;

    public FeeReports(PaymentDao paymentDao, StudentDao studentDao, StudentRegistrationDao registrationDao) {
        this.paymentDao = paymentDao;
        this.studentDao = studentDao;
        this.registrationDao = registrationDao;
    }

    public ReportDocument feeDetails(ReportRequest request, AcademicYear year) {
        Integer studentId = request.requireStudentId();

        Student student = studentDao.findById(studentId)
                .orElseThrow(() -> ApiException.notFound("Student " + studentId + " does not exist."));

        List<ReportColumn> columns = List.of(
                ReportColumn.text("Grade"),
                ReportColumn.text("Year"),
                ReportColumn.text("Receipt No."),
                ReportColumn.text("Method"),
                ReportColumn.number("Due"),
                ReportColumn.number("Paid"),
                ReportColumn.number("Balance"),
                ReportColumn.text("Paid Date"));

        List<List<String>> rows = new ArrayList<>();
        BigDecimal totalDue = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;

        for (Payment payment : paymentDao.listByStudent(studentId)) {
            var enrolment = payment.getStudent_registration_id();
            var classroom = enrolment == null ? null : enrolment.getClassroom_id();

            BigDecimal due = payment.getAmount_due() == null ? BigDecimal.ZERO : payment.getAmount_due();
            BigDecimal paid = payment.getAmount_paid() == null ? BigDecimal.ZERO : payment.getAmount_paid();
            totalDue = totalDue.add(due);
            totalPaid = totalPaid.add(paid);

            rows.add(List.of(
                    classroom == null || classroom.getGrade_id() == null ? "—" : classroom.getGrade_id().getName(),
                    classroom == null || classroom.getAcademic_year_id() == null
                            ? "—"
                            : classroom.getAcademic_year_id().getName(),
                    payment.getBill_no() == null ? "" : payment.getBill_no(),
                    payment.getPayment_type_id() == null ? "—" : payment.getPayment_type_id().getName(),
                    money(payment.getAmount_due()),
                    money(paid),
                    money(payment.getBalance_amount()),
                    payment.getPaid_date() == null ? "" : DATE.format(payment.getPaid_date())));
        }

        List<String> footer = List.of(
                "Total", "", "", "",
                money(totalDue),
                money(totalPaid),
                money(totalDue.subtract(totalPaid)),
                rows.size() + " payment(s)");

        ReportSection section = new ReportSection(
                student.getFullname(),
                "Admission number " + orDash(student.getStu_no()) + " · present grade: " + presentGrade(student),
                columns,
                rows,
                rows.isEmpty() ? null : footer);

        return document(ReportService.FEE_DETAILS,
                "Fees Details — " + student.getFullname(),
                "Every fee payment recorded against this student.",
                year, ReportLayout.PORTRAIT, List.of(section));
    }

    // -------------------------------------------------------------------------

    /** The grade of the student's most recent enrolment. */
    private String presentGrade(Student student) {
        List<StudentRegistration> enrolments = registrationDao.listByStudent(student.getId());
        for (StudentRegistration enrolment : enrolments) {
            var classroom = enrolment.getClassroom_id();
            if (classroom != null && classroom.getGrade_id() != null) {
                return classroom.getGrade_id().getName() + " " + classroom.getName();
            }
        }
        // Falls back to the grade on the student record for anyone not yet
        // placed in a class.
        return student.getGrade_id() == null ? "not enrolled" : student.getGrade_id().getName();
    }

    private String money(BigDecimal value) {
        return value == null ? "—" : String.format("%,.2f", value);
    }

    private String orDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
