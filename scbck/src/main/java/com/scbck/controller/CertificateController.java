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

import com.scbck.model.StudentCertificate;
import com.scbck.service.CertificatePdfService;
import com.scbck.service.CertificateService;
import com.scbck.service.PrivilegeService;

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
    private final PrivilegeService privilegeService;

    public CertificateController(CertificateService certificateService, CertificatePdfService pdfService,
            PrivilegeService privilegeService) {
        this.certificateService = certificateService;
        this.pdfService = pdfService;
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
    public ResponseEntity<StudentCertificate> issue(@RequestBody StudentCertificate certificate) {
        privilegeService.requireUpdate(PrivilegeService.MODULE_STUDENT);
        return ResponseEntity.status(HttpStatus.CREATED).body(certificateService.issue(certificate));
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
