package com.scbck.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.scbck.dto.MarkEntryRequest;
import com.scbck.dto.MarkSheet;
import com.scbck.service.MarkEntryService;
import com.scbck.service.MarkSheetExcelService;
import com.scbck.service.MarkSheetPdfService;
import com.scbck.service.MarkSheetService;
import com.scbck.service.PrivilegeService;

import jakarta.validation.Valid;

/**
 * Subject-wise marks for a class and term: the sheet, entry, and both exports.
 *
 * All four responses come from the same {@link MarkSheetService} call, so the
 * grid a teacher types into, the sheet on screen, the workbook and the PDF are
 * the same numbers. The school's spreadsheets were four separate files by the
 * end of a term and no two agreed on the ranks.
 */
@RestController
@RequestMapping("/api/marks")
public class MarkController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final MarkSheetService markSheetService;
    private final MarkEntryService markEntryService;
    private final MarkSheetExcelService excelService;
    private final MarkSheetPdfService pdfService;
    private final PrivilegeService privilegeService;

    public MarkController(MarkSheetService markSheetService, MarkEntryService markEntryService,
            MarkSheetExcelService excelService, MarkSheetPdfService pdfService,
            PrivilegeService privilegeService) {
        this.markSheetService = markSheetService;
        this.markEntryService = markEntryService;
        this.excelService = excelService;
        this.pdfService = pdfService;
        this.privilegeService = privilegeService;
    }

    /** The calculated sheet: roster, marks, totals, ranks and the analysis. */
    @GetMapping("/sheet")
    public MarkSheet sheet(@RequestParam Integer classroomId, @RequestParam Integer termId) {
        privilegeService.requireMarkEntry();
        return markSheetService.build(classroomId, termId);
    }

    /**
     * Saves a screen's worth of marks and returns the recalculated sheet, so
     * the totals and ranks a teacher sees after saving are the stored ones
     * rather than a client-side guess.
     */
    @PutMapping
    public MarkSheet save(@Valid @RequestBody MarkEntryRequest request) {
        privilegeService.requireMarkEntry();
        markEntryService.save(request);
        return markSheetService.build(request.classroomId(), request.termId());
    }

    @GetMapping("/sheet/excel")
    public ResponseEntity<byte[]> excel(@RequestParam Integer classroomId, @RequestParam Integer termId) {
        privilegeService.requireMarkEntry();

        MarkSheet sheet = markSheetService.build(classroomId, termId);
        return download(excelService.render(sheet), excelService.fileNameFor(sheet), MediaType.parseMediaType(XLSX));
    }

    @GetMapping("/sheet/pdf")
    public ResponseEntity<byte[]> pdf(@RequestParam Integer classroomId, @RequestParam Integer termId) {
        privilegeService.requireMarkEntry();

        MarkSheet sheet = markSheetService.build(classroomId, termId);
        return download(pdfService.render(sheet), pdfService.fileNameFor(sheet), MediaType.APPLICATION_PDF);
    }

    // -------------------------------------------------------------------------

    private ResponseEntity<byte[]> download(byte[] body, String filename, MediaType type) {
        ContentDisposition disposition = ContentDisposition.attachment().filename(filename).build();

        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                // Without this the split-domain deployment loses the filename:
                // the browser cannot read Content-Disposition cross-origin
                // unless it is explicitly exposed.
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentLength(body.length)
                .body(body);
    }
}
