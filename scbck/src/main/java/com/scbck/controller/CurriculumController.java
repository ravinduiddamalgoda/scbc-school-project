package com.scbck.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.scbck.dto.CurriculumEntry;
import com.scbck.exception.ApiException;
import com.scbck.model.Grade;
import com.scbck.model.GradeSubject;
import com.scbck.model.SubjectDetail;
import com.scbck.repository.GradeDao;
import com.scbck.repository.GradeSubjectDao;
import com.scbck.repository.SubjectDetailDao;
import com.scbck.service.PrivilegeService;

/**
 * Which subjects each grade is taught.
 *
 * Read by the timetable editor (to pre-tick a class's subjects instead of
 * offering all twenty-nine), by the marks screen (to know which columns a grade
 * has) and by both subject reports (to count against the curriculum rather than
 * against whatever happens to be ticked).
 *
 * Reading is open to any authenticated user - a curriculum is not record-level
 * data and half the screens are useless without it. Editing shares the Subject
 * module's privileges, on the same reasoning as the categories: whoever may
 * change the subject list may change which grade takes what.
 */
@RestController
@RequestMapping("/api/curriculum")
public class CurriculumController {

    private final GradeSubjectDao gradeSubjectDao;
    private final GradeDao gradeDao;
    private final SubjectDetailDao subjectDao;
    private final PrivilegeService privilegeService;

    public CurriculumController(GradeSubjectDao gradeSubjectDao, GradeDao gradeDao,
            SubjectDetailDao subjectDao, PrivilegeService privilegeService) {
        this.gradeSubjectDao = gradeSubjectDao;
        this.gradeDao = gradeDao;
        this.subjectDao = subjectDao;
        this.privilegeService = privilegeService;
    }

    /**
     * The whole curriculum, or one grade's when {@code gradeId} is given.
     *
     * One endpoint rather than two because the Academic setup screen wants the
     * lot and the timetable editor wants a slice, and the shape is identical.
     */
    @GetMapping
    public List<CurriculumEntry> list(@RequestParam(required = false) Integer gradeId) {
        List<GradeSubject> rows = gradeId == null
                ? gradeSubjectDao.listAll()
                : gradeSubjectDao.listForGrade(gradeId);

        return rows.stream().map(CurriculumController::toEntry).toList();
    }

    /**
     * Replaces one grade's curriculum outright.
     *
     * Whole-list rather than row-by-row for the same reason the timetable is:
     * the decision being recorded is "this is what grade 6 takes", and applying
     * it as a set means a subject removed and one added arrive together instead
     * of leaving the grade briefly holding a curriculum nobody chose.
     *
     * Existing rows are matched by subject and updated in place, so a subject
     * that stays on the curriculum keeps its id - which matters because
     * rewriting every row on every save would churn the table on each edit.
     */
    @PutMapping("/grades/{gradeId}")
    @Transactional
    public List<CurriculumEntry> replace(@PathVariable Integer gradeId,
            @RequestBody List<CurriculumEntry> entries) {

        privilegeService.requireUpdate(PrivilegeService.MODULE_SUBJECT);

        Grade grade = gradeDao.findById(gradeId)
                .orElseThrow(() -> ApiException.notFound("Grade " + gradeId + " does not exist."));

        List<GradeSubject> existing = gradeSubjectDao.listForGrade(gradeId);
        List<GradeSubject> keep = new ArrayList<>();

        int order = 1;
        for (CurriculumEntry entry : entries == null ? List.<CurriculumEntry>of() : entries) {
            if (entry.subjectId() == null) {
                continue;
            }

            SubjectDetail subject = subjectDao.findById(entry.subjectId())
                    .orElseThrow(() -> ApiException.badRequest(
                            "Subject " + entry.subjectId() + " does not exist."));

            GradeSubject row = existing.stream()
                    .filter(candidate -> Objects.equals(candidate.getSubject().getId(),
                            entry.subjectId()))
                    .findFirst()
                    .orElseGet(GradeSubject::new);

            row.setGrade(grade);
            row.setSubject(subject);
            row.setBasket(basketOf(entry.basket()));
            row.setSortOrder(entry.sortOrder() == null ? order : entry.sortOrder());
            row.setClassTeacherTaught(entry.classTeacherTaught());
            keep.add(row);
            order++;
        }

        List<GradeSubject> removed = existing.stream()
                .filter(row -> keep.stream().noneMatch(kept -> Objects.equals(kept.getId(), row.getId())))
                .toList();

        gradeSubjectDao.deleteAll(removed);
        gradeSubjectDao.saveAll(keep);

        return gradeSubjectDao.listForGrade(gradeId).stream()
                .map(CurriculumController::toEntry)
                .toList();
    }

    // -------------------------------------------------------------------------

    /** Unrecognised basket names fall back to Core rather than being rejected. */
    private static String basketOf(String basket) {
        if (basket == null || basket.isBlank()) {
            return GradeSubject.CORE;
        }
        return switch (basket.trim()) {
            case GradeSubject.CATEGORY_1 -> GradeSubject.CATEGORY_1;
            case GradeSubject.CATEGORY_2 -> GradeSubject.CATEGORY_2;
            case GradeSubject.CATEGORY_3 -> GradeSubject.CATEGORY_3;
            case GradeSubject.GENERAL -> GradeSubject.GENERAL;
            default -> GradeSubject.CORE;
        };
    }

    private static CurriculumEntry toEntry(GradeSubject row) {
        return new CurriculumEntry(
                row.getId(),
                row.getGrade() == null ? null : row.getGrade().getId(),
                row.getGrade() == null ? null : row.getGrade().getName(),
                row.getSubject() == null ? null : row.getSubject().getId(),
                row.getSubject() == null ? null : row.getSubject().getName(),
                row.getSubject() == null ? null : row.getSubject().getCode(),
                row.getBasket(),
                row.getSortOrder(),
                Boolean.TRUE.equals(row.getClassTeacherTaught()));
    }
}
