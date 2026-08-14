package com.scbck.controller;

import java.util.List;
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

import com.scbck.dto.DistributionSheet;
import com.scbck.dto.MessageResponse;
import com.scbck.exception.ApiException;
import com.scbck.model.DistributionItem;
import com.scbck.repository.DistributionDao;
import com.scbck.repository.DistributionItemDao;
import com.scbck.service.DistributionExcelService;
import com.scbck.service.DistributionService;
import com.scbck.service.PrivilegeService;

import jakarta.validation.Valid;

/**
 * Uniform and book distribution: the items, the sheet, entry, and the export.
 *
 * Gated on the Student module - handing out a uniform is an act on a student's
 * record, and the office staff who do it already hold those rights. A module of
 * its own would mean granting the same people the same access twice.
 */
@RestController
@RequestMapping("/api/distributions")
public class DistributionController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final DistributionService distributionService;
    private final DistributionExcelService excelService;
    private final DistributionItemDao itemDao;
    private final DistributionDao distributionDao;
    private final PrivilegeService privilegeService;

    public DistributionController(DistributionService distributionService,
            DistributionExcelService excelService, DistributionItemDao itemDao,
            DistributionDao distributionDao, PrivilegeService privilegeService) {
        this.distributionService = distributionService;
        this.excelService = excelService;
        this.itemDao = itemDao;
        this.distributionDao = distributionDao;
        this.privilegeService = privilegeService;
    }

    // ---- The sheet -----------------------------------------------------------

    @GetMapping("/sheet")
    public DistributionSheet sheet(@RequestParam Integer classroomId, @RequestParam String kind) {
        privilegeService.requireSelect(PrivilegeService.MODULE_STUDENT);
        return distributionService.build(classroomId, kind);
    }

    /** Saves a screen's worth and returns the sheet as stored. */
    @PutMapping
    public DistributionSheet save(@RequestBody SaveRequest request) {
        privilegeService.requireUpdate(PrivilegeService.MODULE_STUDENT);
        distributionService.save(request.classroomId(), request.kind(), request.entries());
        return distributionService.build(request.classroomId(), request.kind());
    }

    @GetMapping("/sheet/excel")
    public ResponseEntity<byte[]> excel(@RequestParam Integer classroomId, @RequestParam String kind) {
        privilegeService.requireSelect(PrivilegeService.MODULE_STUDENT);

        DistributionSheet sheet = distributionService.build(classroomId, kind);
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

    // ---- The items -----------------------------------------------------------

    @GetMapping("/items")
    public List<DistributionItem> items() {
        privilegeService.requireSelect(PrivilegeService.MODULE_STUDENT);
        return itemDao.listAll();
    }

    @PostMapping("/items")
    @Transactional
    public ResponseEntity<DistributionItem> createItem(@Valid @RequestBody DistributionItem item) {
        privilegeService.requireInsert(PrivilegeService.MODULE_STUDENT);

        item.setId(null);
        normalise(item);
        return ResponseEntity.status(HttpStatus.CREATED).body(itemDao.save(item));
    }

    @PutMapping("/items/{id}")
    @Transactional
    public DistributionItem updateItem(@PathVariable Integer id, @Valid @RequestBody DistributionItem item) {
        privilegeService.requireUpdate(PrivilegeService.MODULE_STUDENT);

        DistributionItem existing = itemDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Item " + id + " does not exist."));

        existing.setName(item.getName());
        existing.setCode(item.getCode());
        existing.setKind(item.getKind());
        existing.setGradeId(item.getGradeId());
        existing.setSortOrder(item.getSortOrder());
        existing.setActive(item.getActive());
        normalise(existing);

        return itemDao.save(existing);
    }

    /**
     * Deletes only while nothing has been issued against the item.
     *
     * Removing one that has been handed out would erase the record of who
     * collected what, which is the only thing the sheet exists to prove.
     */
    @DeleteMapping("/items/{id}")
    @Transactional
    public MessageResponse deleteItem(@PathVariable Integer id) {
        privilegeService.requireDelete(PrivilegeService.MODULE_STUDENT);

        DistributionItem existing = itemDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Item " + id + " does not exist."));

        long issued = distributionDao.countByItem(id);
        if (issued > 0) {
            throw ApiException.conflict(existing.getName() + " has already been issued to " + issued
                    + " student(s). Mark it inactive to retire it instead.");
        }

        itemDao.delete(existing);
        return MessageResponse.of(existing.getName() + " deleted.");
    }

    // -------------------------------------------------------------------------

    private void normalise(DistributionItem item) {
        if (item.getName() == null || item.getName().isBlank()) {
            throw ApiException.badRequest("An item name is required.");
        }
        item.setName(item.getName().trim());

        String kind = item.getKind() == null ? "" : item.getKind().trim().toUpperCase();
        if (!Objects.equals(DistributionItem.UNIFORM, kind) && !Objects.equals(DistributionItem.BOOK, kind)) {
            throw ApiException.badRequest("An item is either UNIFORM or BOOK, not '" + item.getKind() + "'.");
        }
        item.setKind(kind);

        if (item.getCode() != null && item.getCode().isBlank()) {
            item.setCode(null);
        }
        if (item.getSortOrder() == null) {
            item.setSortOrder(0);
        }
        if (item.getActive() == null) {
            item.setActive(Boolean.TRUE);
        }
    }

    /** @param entries one per cell the clerk touched */
    public record SaveRequest(Integer classroomId, String kind, List<DistributionService.Entry> entries) {
    }
}
