package com.scbck.controller;

import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.scbck.dto.CertificateRequest;
import com.scbck.model.StudentCertificate;
import com.scbck.service.CertificateLogExcelService;
import com.scbck.service.CertificatePdfService;
import com.scbck.service.CertificateService;
import com.scbck.service.PrivilegeService;

import jakarta.validation.Valid;

/**
 * Leaving and character certificates: draft, issue, reprint.
 *
 * Gated on the Student module - a certificate is a statement about a student's
 * record, so whoever may read that record may draft one, and only someone who
 * may change it may issue one.
 */
@RestController
@RequestMapping("/api/certificates")
public class CertificateController {

    private final CertificateService certificateService;
    private final CertificatePdfService pdfService;
    private final CertificateLogExcelService logExcelService;
    private final PrivilegeService privilegeService;

    public CertificateController(CertificateService certificateService, CertificatePdfService pdfService,
            CertificateLogExcelService logExcelService, PrivilegeService privilegeService) {
        this.certificateService = certificateService;
        this.pdfService = pdfService;
        this.logExcelService = logExcelService;
        this.privilegeService = privilegeService;
    }

    /**
     * A draft with everything the school already knows filled in.
     *
     * Nothing is stored: the principal reviews the wording first.
     */
    @GetMapping("/draft")
    public StudentCertificate draft(@RequestParam Integer studentId, @RequestParam String type) {
        privilegeService.requireSelect(PrivilegeService.MODULE_STUDENT);
        return certificateService.prefill(studentId, type);
    }

    /** The issue log, or one student's certificates. */
    @GetMapping
    public List<StudentCertificate> list(@RequestParam(required = false) Integer studentId) {
        privilegeService.requireSelect(PrivilegeService.MODULE_STUDENT);
        return studentId == null
                ? certificateService.listRecent()
                : certificateService.listFor(studentId);
    }

    /** Records an issued certificate, exactly as worded. */
    @PostMapping
    public ResponseEntity<StudentCertificate> issue(@Valid @RequestBody CertificateRequest request) {
        privilegeService.requireUpdate(PrivilegeService.MODULE_STUDENT);
        return ResponseEntity.status(HttpStatus.CREATED).body(certificateService.issue(request));
    }

    /**
     * The register of everything issued, as a workbook.
     *
     * Answers "who has been given a certificate, and when" without opening each
     * one - the question a parent, an auditor or a receiving school actually
     * asks.
     */
    @GetMapping("/register/excel")
    public ResponseEntity<byte[]> register(@RequestParam(required = false) Integer studentId) {
        privilegeService.requireSelect(PrivilegeService.MODULE_STUDENT);

        List<StudentCertificate> issued = studentId == null
                ? certificateService.listRecent()
                : certificateService.listFor(studentId);

        byte[] body = logExcelService.render(issued);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("Certificates Issued.xlsx")
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentLength(body.length)
                .body(body);
    }

    /**
     * A previously issued certificate as PDF.
     *
     * Rendered from the stored text, so a reprint years later is the same
     * document that was signed rather than one rebuilt from a record that has
     * since changed.
     */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Integer id) {
        privilegeService.requireSelect(PrivilegeService.MODULE_STUDENT);

        StudentCertificate certificate = certificateService.require(id);
        byte[] body = pdfService.render(certificate);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(pdfService.fileNameFor(certificate))
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentLength(body.length)
                .body(body);
    }
}
