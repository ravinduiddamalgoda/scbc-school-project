package com.scbck.controller;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.scbck.dto.GradeHeadResponse;
import com.scbck.dto.MessageResponse;
import com.scbck.exception.ApiException;
import com.scbck.model.AcademicYear;
import com.scbck.model.Employee;
import com.scbck.model.Grade;
import com.scbck.model.GradeHead;
import com.scbck.repository.EmployeeDao;
import com.scbck.repository.GradeDao;
import com.scbck.repository.GradeHeadDao;
import com.scbck.service.AcademicYearService;
import com.scbck.service.PrivilegeService;

/**
 * Which teacher heads each grade, for one academic year.
 *
 * There is no create/delete pair here because the thing being edited is a
 * fixed list - every grade has exactly one slot. Assigning is a PUT on the
 * grade; clearing it is a DELETE. That keeps the screen from being able to
 * produce two heads for one grade, which the unique constraint also refuses.
 */
@RestController
@RequestMapping("/api/grade-heads")
public class GradeHeadController {

    private final GradeHeadDao gradeHeadDao;
    private final GradeDao gradeDao;
    private final EmployeeDao employeeDao;
    private final AcademicYearService academicYearService;
    private final PrivilegeService privilegeService;

    public GradeHeadController(GradeHeadDao gradeHeadDao, GradeDao gradeDao, EmployeeDao employeeDao,
            AcademicYearService academicYearService, PrivilegeService privilegeService) {
        this.gradeHeadDao = gradeHeadDao;
        this.gradeDao = gradeDao;
        this.employeeDao = employeeDao;
        this.academicYearService = academicYearService;
        this.privilegeService = privilegeService;
    }

    /** Every grade, with its head where one has been named. */
    @GetMapping
    public List<GradeHeadResponse> findAll(@RequestParam(required = false) Integer academicYearId) {
        privilegeService.requireSelect(PrivilegeService.MODULE_CLASS);

        AcademicYear year = academicYearService.resolve(academicYearId);

        Map<Integer, GradeHead> assigned = gradeHeadDao.listByAcademicYear(year.getId()).stream()
                .collect(Collectors.toMap(head -> head.getGrade_id().getId(), Function.identity()));

        return gradeDao.findAll(Sort.by("id")).stream()
                .map(grade -> {
                    GradeHead head = assigned.get(grade.getId());
                    return head == null ? GradeHeadResponse.unassigned(grade) : GradeHeadResponse.of(head);
                })
                .toList();
    }

    /** Names (or renames) the head of one grade. */
    @PutMapping("/{gradeId}")
    @Transactional
    public GradeHeadResponse assign(@PathVariable Integer gradeId,
            @RequestBody Map<String, Integer> body,
            @RequestParam(required = false) Integer academicYearId) {
        privilegeService.requireUpdate(PrivilegeService.MODULE_CLASS);

        Integer employeeId = body.get("employeeId");
        if (employeeId == null) {
            throw ApiException.badRequest("Choose the teacher who heads this grade.");
        }

        AcademicYear year = academicYearService.resolve(academicYearId);

        Grade grade = gradeDao.findById(gradeId)
                .orElseThrow(() -> ApiException.notFound("Grade " + gradeId + " does not exist."));
        Employee employee = employeeDao.findById(employeeId)
                .orElseThrow(() -> ApiException.badRequest("Employee " + employeeId + " does not exist."));

        GradeHead existing = gradeHeadDao.getByYearAndGrade(year.getId(), gradeId);
        if (existing == null) {
            existing = new GradeHead();
            existing.setGrade_id(grade);
            existing.setAcademic_year_id(year);
        }
        existing.setEmployee_id(employee);

        return GradeHeadResponse.of(gradeHeadDao.save(existing));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public MessageResponse clear(@PathVariable Integer id) {
        privilegeService.requireDelete(PrivilegeService.MODULE_CLASS);

        GradeHead existing = gradeHeadDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Grade head " + id + " does not exist."));

        String grade = existing.getGrade_id().getName();
        gradeHeadDao.delete(existing);

        return MessageResponse.of("Grade head cleared for " + grade + ".");
    }
}
