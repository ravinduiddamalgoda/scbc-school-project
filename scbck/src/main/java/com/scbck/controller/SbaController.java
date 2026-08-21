package com.scbck.controller;

import java.util.List;
import java.util.Map;

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

import com.scbck.dto.SbaSheet;
import com.scbck.model.SbaMark;
import com.scbck.service.PrivilegeService;
import com.scbck.service.SbaExcelService;
import com.scbck.service.SbaService;

/**
 * School Based Assessment marks, and the Department's workbook.
 *
 * Separate from the Marks module because it is a separate thing: an SBA mark is
 * coursework submitted upwards, entered by the subject teacher over two years,
 * and a term examination result is the school's own. Gating them on separate
 * privilege modules lets the school give the A/L coordinator the assessment
 * without the report marks, which is how they actually work.
 */
@RestController
@RequestMapping("/api/sba")
public class SbaController {

    private static final String XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final SbaService sbaService;
    private final SbaExcelService excelService;
    private final PrivilegeService privilegeService;

    public SbaController(SbaService sbaService, SbaExcelService excelService,
            PrivilegeService privilegeService) {
        this.sbaService = sbaService;
        this.excelService = excelService;
        this.privilegeService = privilegeService;
    }

    /**
     * Which grades and terms each examination is assessed over.
     *
     * Served rather than hard-coded in the browser so the entry screen's grade
     * and term pickers offer exactly what the server will accept - the A/L
     * sheet takes grade 13 terms 1 and 2 and grade 12 terms 1 to 3, and getting
     * that wrong in the client shows up only as a rejected save.
     */
    @GetMapping("/structure")
    public Map<String, Object> structure() {
        privilegeService.requireSelect(PrivilegeService.MODULE_SBA);

        return Map.of(
                "exams", SbaMark.EXAMS,
                "AL", Map.of(
                        "label", "G.C.E. A/L",
                        "grades", SbaMark.gradesFor(SbaMark.AL),
                        "terms", Map.of(
                                "12", SbaMark.termsFor(SbaMark.AL, 12),
                                "13", SbaMark.termsFor(SbaMark.AL, 13))),
                "OL", Map.of(
                        "label", "G.C.E. O/L",
                        "grades", SbaMark.gradesFor(SbaMark.OL),
                        "terms", Map.of(
                                "10", SbaMark.termsFor(SbaMark.OL, 10),
                                "11", SbaMark.termsFor(SbaMark.OL, 11))));
    }

    /** The merged sheet for one subject: five assessment columns and a total. */
    @GetMapping("/sheet")
    public SbaSheet sheet(@RequestParam String exam,
            @RequestParam(required = false) Integer examYear,
            @RequestParam Integer subjectId,
            @RequestParam(required = false) String medium) {

        privilegeService.requireSelect(PrivilegeService.MODULE_SBA);
        return sbaService.sheet(exam, examYear, subjectId, medium);
    }

    /**
     * Saves one entry grid: one subject, one grade, one term.
     *
     * Returns the recalculated sheet rather than an acknowledgement, so the
     * totals on screen after saving are the ones the server computed - the page
     * never does the arithmetic itself.
     */
    @PutMapping("/marks")
    public SbaSheet save(@RequestParam String exam,
            @RequestParam(required = false) Integer examYear,
            @RequestParam Integer subjectId,
            @RequestParam Integer grade,
            @RequestParam Integer term,
            @RequestParam(required = false) String medium,
            @RequestBody List<SbaService.Entry> entries) {

        return sbaService.saveEntries(exam, examYear, subjectId, grade, term, entries, medium);
    }

    /** The sheet as the workbook the Department is sent. */
    @GetMapping("/sheet/excel")
    public ResponseEntity<byte[]> excel(@RequestParam String exam,
            @RequestParam(required = false) Integer examYear,
            @RequestParam Integer subjectId,
            @RequestParam(required = false) String medium) {

        privilegeService.requireSelect(PrivilegeService.MODULE_SBA);

        SbaSheet sheet = sbaService.sheet(exam, examYear, subjectId, medium);
        byte[] body = excelService.render(sheet);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(excelService.fileNameFor(sheet))
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentLength(body.length)
                .body(body);
    }
}
