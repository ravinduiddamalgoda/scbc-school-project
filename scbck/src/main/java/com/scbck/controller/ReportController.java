package com.scbck.controller;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.scbck.dto.ReportDocument;
import com.scbck.dto.ReportRequest;
import com.scbck.dto.ReportSummary;
import com.scbck.exception.ApiException;
import com.scbck.service.PrivilegeService;
import com.scbck.service.ReportPdfService;
import com.scbck.service.ReportService;

/**
 * The report catalogue, each report as JSON, and each report as PDF.
 *
 * The JSON and the PDF come from the same {@link ReportService} call, so what
 * the screen shows and what the printout says can never drift apart - the old
 * workbooks drifted precisely because they were maintained separately from the
 * records they described.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final ReportPdfService reportPdfService;
    private final PrivilegeService privilegeService;

    public ReportController(ReportService reportService, ReportPdfService reportPdfService,
            PrivilegeService privilegeService) {
        this.reportService = reportService;
        this.reportPdfService = reportPdfService;
        this.privilegeService = privilegeService;
    }

    /** What can be run. The client builds its report menu from this. */
    @GetMapping
    public List<ReportSummary> catalogue() {
        privilegeService.requireSelect(PrivilegeService.MODULE_REPORT);
        return reportService.catalogue();
    }

    /**
     * @param month "2026-08". Only the register uses it; the rest ignore it,
     *              which is what lets the client send whatever controls the
     *              catalogue told it to render without special-casing a report.
     */
    @GetMapping("/{key}")
    public ReportDocument run(@PathVariable String key,
            @RequestParam(required = false) Integer academicYearId,
            @RequestParam(required = false) Integer classroomId,
            @RequestParam(required = false) Integer studentId,
            @RequestParam(required = false) String month) {
        privilegeService.requireSelect(PrivilegeService.MODULE_REPORT);
        return reportService.build(key, toRequest(academicYearId, classroomId, studentId, month));
    }

    /**
     * The same report as a downloadable PDF.
     *
     * The filename is set in Content-Disposition rather than left to the
     * browser, so a saved report says which report and which year it is.
     */
    @GetMapping("/{key}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String key,
            @RequestParam(required = false) Integer academicYearId,
            @RequestParam(required = false) Integer classroomId,
            @RequestParam(required = false) Integer studentId,
            @RequestParam(required = false) String month) {
        privilegeService.requireSelect(PrivilegeService.MODULE_REPORT);

        ReportDocument report = reportService.build(key,
                toRequest(academicYearId, classroomId, studentId, month));
        byte[] pdf = reportPdfService.render(report);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(reportService.fileNameFor(report))
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                // The browser cannot read Content-Disposition on a cross-origin
                // response unless it is explicitly exposed; without this the split
                // domain deployment loses the filename.
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentLength(pdf.length)
                .body(pdf);
    }

    // -------------------------------------------------------------------------

    private ReportRequest toRequest(Integer academicYearId, Integer classroomId, Integer studentId, String month) {
        YearMonth parsed = null;
        if (month != null && !month.isBlank()) {
            try {
                parsed = YearMonth.parse(month.trim());
            } catch (DateTimeParseException error) {
                throw ApiException.badRequest("month must look like 2026-08, not '" + month + "'.");
            }
        }
        return new ReportRequest(academicYearId, classroomId, studentId, parsed);
    }
}
