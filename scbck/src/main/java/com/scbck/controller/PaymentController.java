package com.scbck.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.scbck.dto.FeePosition;
import com.scbck.dto.MessageResponse;
import com.scbck.dto.PaymentRequest;
import com.scbck.dto.PaymentResponse;
import com.scbck.exception.ApiException;
import com.scbck.model.Payment;
import com.scbck.model.Student;
import com.scbck.model.StudentRegistration;
import com.scbck.repository.PaymentDao;
import com.scbck.repository.PaymentTypeDao;
import com.scbck.repository.StudentDao;
import com.scbck.repository.StudentRegistrationDao;
import com.scbck.service.FeeService;
import com.scbck.service.PaymentHistoryPdfService;
import com.scbck.service.PrivilegeService;

import jakarta.validation.Valid;

/**
 * Fee payments received.
 *
 * This records money that came in; it is not a billing engine, and it does not
 * raise invoices or chase them. What it does now know is what a grade's fee is:
 * "amount due" is offered from {@code FeeStructure} rather than typed on every
 * receipt, which is what made the outstanding-balance column disagree with
 * itself.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentDao paymentDao;
    private final PaymentTypeDao paymentTypeDao;
    private final StudentDao studentDao;
    private final StudentRegistrationDao registrationDao;
    private final PrivilegeService privilegeService;
    private final FeeService feeService;
    private final PaymentHistoryPdfService historyPdfService;

    public PaymentController(PaymentDao paymentDao, PaymentTypeDao paymentTypeDao, StudentDao studentDao,
            StudentRegistrationDao registrationDao, PrivilegeService privilegeService,
            FeeService feeService, PaymentHistoryPdfService historyPdfService) {
        this.historyPdfService = historyPdfService;
        this.paymentDao = paymentDao;
        this.paymentTypeDao = paymentTypeDao;
        this.studentDao = studentDao;
        this.registrationDao = registrationDao;
        this.privilegeService = privilegeService;
        this.feeService = feeService;
    }

    /**
     * Finds the student a receipt is for by admission number or name.
     *
     * The payment form previously offered a dropdown of every student in the
     * school - nearly three thousand of them - which is unusable when the clerk
     * has a number from a paper file and nothing else. Leading zeroes are
     * optional: "3960" finds "00003960".
     */
    @GetMapping("/students")
    public List<Map<String, Object>> findStudents(@RequestParam String q) {
        // Reached from the payments screen and from the attendance letters, by
        // people holding different rights - see requireAnySelect.
        privilegeService.requireAnySelect(PrivilegeService.MODULE_PAYMENT,
                PrivilegeService.MODULE_ATTENDANCE, PrivilegeService.MODULE_STUDENT);

        String term = q == null ? "" : q.trim();
        if (term.isEmpty()) {
            return List.of();
        }

        List<Student> found = new ArrayList<>();

        // An exact admission number goes first and alone: when the clerk has
        // typed the number, showing the forty students whose name contains it
        // is noise.
        Student exact = studentDao.getByAdmissionNo(term);
        if (exact != null) {
            found.add(exact);
        } else {
            found.addAll(studentDao.search(term).stream().limit(25).toList());
        }

        return found.stream().map(student -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", student.getId());
            row.put("admissionNo", student.getStu_no());
            row.put("fullname", student.getFullname());
            row.put("grade", student.getGrade_id() == null ? null : student.getGrade_id().getName());
            return row;
        }).toList();
    }

    /**
     * What the student owes for a year, what they have paid, and every receipt.
     *
     * One call rather than three because the payment screen shows all of it at
     * once: the clerk opens a student, sees the balance, and prints the
     * history from the same panel.
     */
    @GetMapping("/fee-position")
    public FeePosition feePosition(@RequestParam Integer studentId,
            @RequestParam(required = false) Integer academicYearId) {
        privilegeService.requireSelect(PrivilegeService.MODULE_PAYMENT);
        return feeService.positionOf(studentId, academicYearId);
    }

    /** The same history as a printable page for the counter. */
    @GetMapping("/fee-position/pdf")
    public ResponseEntity<byte[]> feePositionPdf(@RequestParam Integer studentId,
            @RequestParam(required = false) Integer academicYearId) {

        privilegeService.requireSelect(PrivilegeService.MODULE_PAYMENT);

        FeePosition position = feeService.positionOf(studentId, academicYearId);
        byte[] body = historyPdfService.render(position);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(historyPdfService.fileNameFor(position))
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentLength(body.length)
                .body(body);
    }

    @GetMapping
    public List<PaymentResponse> findAll(@RequestParam(required = false) Integer studentId) {
        privilegeService.requireSelect(PrivilegeService.MODULE_PAYMENT);

        List<Payment> payments = studentId == null
                ? paymentDao.listNewestFirst()
                : paymentDao.listByStudent(studentId);

        return payments.stream().map(PaymentResponse::of).toList();
    }

    @GetMapping("/{id}")
    public PaymentResponse findById(@PathVariable Integer id) {
        privilegeService.requireSelect(PrivilegeService.MODULE_PAYMENT);
        return PaymentResponse.of(require(id));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody PaymentRequest request) {
        privilegeService.requireInsert(PrivilegeService.MODULE_PAYMENT);

        Payment payment = new Payment();
        apply(request, payment, null);

        if (payment.getBill_no() == null) {
            payment.setBill_no(String.format("%08d", paymentDao.nextBillSequence()));
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentResponse.of(paymentDao.save(payment)));
    }

    @PutMapping("/{id}")
    @Transactional
    public PaymentResponse update(@PathVariable Integer id, @Valid @RequestBody PaymentRequest request) {
        privilegeService.requireUpdate(PrivilegeService.MODULE_PAYMENT);

        Payment existing = require(id);
        apply(request, existing, id);

        return PaymentResponse.of(paymentDao.save(existing));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public MessageResponse delete(@PathVariable Integer id) {
        privilegeService.requireDelete(PrivilegeService.MODULE_PAYMENT);

        Payment existing = require(id);
        String billNo = existing.getBill_no();

        paymentDao.delete(existing);
        return MessageResponse.of("Receipt " + billNo + " deleted.");
    }

    // -------------------------------------------------------------------------

    private void apply(PaymentRequest request, Payment payment, Integer selfId) {
        Student student = studentDao.findById(request.studentId())
                .orElseThrow(() -> ApiException.badRequest("Student " + request.studentId() + " does not exist."));

        if (request.paidDate().isAfter(LocalDate.now())) {
            throw ApiException.badRequest("A payment cannot be dated in the future.");
        }

        StudentRegistration enrolment = null;
        if (request.enrolmentId() != null) {
            enrolment = registrationDao.findById(request.enrolmentId())
                    .orElseThrow(() -> ApiException
                            .badRequest("Enrolment " + request.enrolmentId() + " does not exist."));

            // Attaching a payment to somebody else's enrolment would file the
            // receipt under the wrong grade in the fee history.
            if (!Objects.equals(enrolment.getStudent_id().getId(), student.getId())) {
                throw ApiException.badRequest("That enrolment belongs to a different student.");
            }
        }

        if (request.billNo() != null && !request.billNo().isBlank()) {
            Payment clash = paymentDao.getByBillNo(request.billNo().trim());
            if (clash != null && !Objects.equals(clash.getId(), selfId)) {
                throw ApiException.conflict("Receipt number " + request.billNo().trim() + " is already used.");
            }
            payment.setBill_no(request.billNo().trim());
        }

        payment.setStudent_id(student);
        payment.setStudent_registration_id(enrolment);
        payment.setAmount_paid(request.amountPaid());
        payment.setAmount_due(request.amountDue());
        payment.setPaid_date(request.paidDate());
        payment.setPayment_type_id(request.paymentTypeId() == null ? null
                : paymentTypeDao.findById(request.paymentTypeId())
                        .orElseThrow(() -> ApiException
                                .badRequest("Payment type " + request.paymentTypeId() + " does not exist.")));

        // Derived on save rather than accepted from the client, so a receipt
        // can never claim a balance its own figures contradict.
        payment.setBalance_amount(request.amountDue() == null
                ? BigDecimal.ZERO
                : request.amountDue().subtract(request.amountPaid()));
    }

    private Payment require(Integer id) {
        return paymentDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Payment " + id + " does not exist."));
    }
}
