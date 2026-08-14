package com.scbck.controller;

import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.scbck.service.ExamExportService;
import com.scbck.service.PrivilegeService;

/**
 * The Department of Examinations candidate workbooks.
 *
 * The export reports the records it could not complete as well as producing the
 * file, because the Department rejects a bad upload in bulk without saying
 * which row was at fault. `problems` is what the office works through before
 * submitting; the workbook downloads either way so the gaps can be seen in
 * place.
 */
@RestController
@RequestMapping("/api/exam-exports")
public class ExamExportController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ExamExportService examExportService;
    private final PrivilegeService privilegeService;

    public ExamExportController(ExamExportService examExportService, PrivilegeService privilegeService) {
        this.examExportService = examExportService;
        this.privilegeService = privilegeService;
    }

    /** A dry run: how many candidates, and what needs fixing first. */
    @GetMapping("/check")
    public CheckResponse check(@RequestParam String exam,
            @RequestParam(required = false) Integer academicYearId) {
        privilegeService.requireSelect(PrivilegeService.MODULE_STUDENT);

        ExamExportService.Export export = examExportService.build(exam, academicYearId);
        return new CheckResponse(export.candidates(), export.filename(), export.problems());
    }

    @GetMapping
    public ResponseEntity<byte[]> export(@RequestParam String exam,
            @RequestParam(required = false) Integer academicYearId) {
        privilegeService.requireSelect(PrivilegeService.MODULE_STUDENT);

        ExamExportService.Export export = examExportService.build(exam, academicYearId);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(export.filename())
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentLength(export.workbook().length)
                .body(export.workbook());
    }

    public record CheckResponse(int candidates, String filename, List<String> problems) {
    }
}
