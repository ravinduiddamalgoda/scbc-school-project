package com.scbck.controller;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.http.HttpStatus;
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

import com.scbck.dto.ClassroomRequest;
import com.scbck.dto.ClassroomResponse;
import com.scbck.dto.ClassroomSubjectRequest;
import com.scbck.dto.ClassroomSubjectResponse;
import com.scbck.dto.EnrolmentResponse;
import com.scbck.dto.MessageResponse;
import com.scbck.exception.ApiException;
import com.scbck.model.AcademicYear;
import com.scbck.model.Classroom;
import com.scbck.model.ClassroomSubject;
import com.scbck.model.Employee;
import com.scbck.model.Grade;
import com.scbck.model.StudentRegistration;
import com.scbck.model.SubjectDetail;
import com.scbck.repository.ClassroomDao;
import com.scbck.repository.ClassroomSubjectDao;
import com.scbck.repository.EmployeeDao;
import com.scbck.repository.GradeDao;
import com.scbck.repository.StudentRegistrationDao;
import com.scbck.repository.StudentSubjectDao;
import com.scbck.repository.SubjectDetailDao;
import com.scbck.repository.projection.CountByKey;
import com.scbck.service.AcademicYearService;
import com.scbck.service.PrivilegeService;

import jakarta.validation.Valid;

/**
 * Class CRUD, plus the two things that hang off a class: its timetable (which
 * subject is taught by whom) and its roll.
 *
 * The classroom table is in the ER model and was never implemented, which is
 * why three of the four report spreadsheets had nothing to read - "which class
 * is a student in", "who is the class teacher" and "who teaches this subject"
 * were all facts the database could not hold.
 */
@RestController
@RequestMapping("/api/classes")
public class ClassroomController {

    private final ClassroomDao classroomDao;
    private final ClassroomSubjectDao classroomSubjectDao;
    private final StudentRegistrationDao registrationDao;
    private final StudentSubjectDao studentSubjectDao;
    private final SubjectDetailDao subjectDao;
    private final GradeDao gradeDao;
    private final EmployeeDao employeeDao;
    private final AcademicYearService academicYearService;
    private final PrivilegeService privilegeService;

    public ClassroomController(ClassroomDao classroomDao, ClassroomSubjectDao classroomSubjectDao,
            StudentRegistrationDao registrationDao, StudentSubjectDao studentSubjectDao,
            SubjectDetailDao subjectDao, GradeDao gradeDao, EmployeeDao employeeDao,
            AcademicYearService academicYearService, PrivilegeService privilegeService) {
        this.classroomDao = classroomDao;
        this.classroomSubjectDao = classroomSubjectDao;
        this.registrationDao = registrationDao;
        this.studentSubjectDao = studentSubjectDao;
        this.subjectDao = subjectDao;
        this.gradeDao = gradeDao;
        this.employeeDao = employeeDao;
        this.academicYearService = academicYearService;
        this.privilegeService = privilegeService;
    }

    /**
     * Every class in one academic year. The subject and student counts come
     * from two grouped queries rather than a lookup per row.
     */
    @GetMapping
    public List<ClassroomResponse> findAll(@RequestParam(required = false) Integer academicYearId) {
        privilegeService.requireSelect(PrivilegeService.MODULE_CLASS);

        AcademicYear year = academicYearService.resolve(academicYearId);

        Map<Integer, Long> subjectCounts = CountByKey.toMap(classroomSubjectDao.countByClassroom(year.getId()));
        Map<Integer, Long> studentCounts = CountByKey.toMap(registrationDao.countActiveByClassroom(year.getId()));

        return classroomDao.listByAcademicYear(year.getId()).stream()
                .map(classroom -> ClassroomResponse.of(classroom,
                        subjectCounts.getOrDefault(classroom.getId(), 0L),
                        studentCounts.getOrDefault(classroom.getId(), 0L)))
                .toList();
    }

    @GetMapping("/{id}")
    public ClassroomResponse findById(@PathVariable Integer id) {
        privilegeService.requireSelect(PrivilegeService.MODULE_CLASS);

        Classroom classroom = require(id);
        return ClassroomResponse.of(classroom,
                classroomSubjectDao.listByClassroom(id).size(),
                registrationDao.listByClassroom(id).size());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ClassroomResponse> create(@Valid @RequestBody ClassroomRequest request) {
        privilegeService.requireInsert(PrivilegeService.MODULE_CLASS);

        Classroom classroom = new Classroom();
        apply(request, classroom, null);

        Classroom saved = classroomDao.save(classroom);
        return ResponseEntity.status(HttpStatus.CREATED).body(ClassroomResponse.of(saved, 0L, 0L));
    }

    @PutMapping("/{id}")
    @Transactional
    public ClassroomResponse update(@PathVariable Integer id, @Valid @RequestBody ClassroomRequest request) {
        privilegeService.requireUpdate(PrivilegeService.MODULE_CLASS);

        Classroom existing = require(id);
        apply(request, existing, id);

        Classroom saved = classroomDao.save(existing);
        return ClassroomResponse.of(saved,
                classroomSubjectDao.listByClassroom(id).size(),
                registrationDao.listByClassroom(id).size());
    }

    /**
     * Hard delete. A class holding enrolments is refused: those rows are the
     * source of every student-count report, and cascading the delete would
     * quietly erase a year of history.
     */
    @DeleteMapping("/{id}")
    @Transactional
    public MessageResponse delete(@PathVariable Integer id) {
        privilegeService.requireDelete(PrivilegeService.MODULE_CLASS);

        Classroom existing = require(id);

        long enrolments = classroomDao.countEnrolments(id);
        if (enrolments > 0) {
            throw ApiException.conflict(label(existing) + " still has " + enrolments
                    + " student(s) on its roll. Move them to another class first.");
        }

        List<Integer> timetableIds = classroomSubjectDao.listByClassroom(id).stream()
                .map(ClassroomSubject::getId)
                .toList();
        if (!timetableIds.isEmpty()) {
            classroomSubjectDao.deleteAllById(timetableIds);
        }

        classroomDao.delete(existing);
        return MessageResponse.of(label(existing) + " deleted.");
    }

    // ---- Timetable ----------------------------------------------------------

    @GetMapping("/{id}/subjects")
    public List<ClassroomSubjectResponse> subjects(@PathVariable Integer id) {
        privilegeService.requireSelect(PrivilegeService.MODULE_CLASS);
        require(id);

        return classroomSubjectDao.listByClassroom(id).stream()
                .map(link -> ClassroomSubjectResponse.of(link, classroomSubjectDao.countStudents(link.getId())))
                .toList();
    }

    /**
     * Replaces the whole timetable for a class.
     *
     * A full replacement rather than per-line edits, because that is how the
     * screen presents it - a checklist of subjects. Lines that survive keep
     * their id, so the students already enrolled in them keep their subject;
     * only lines genuinely removed take their enrolments with them, and the
     * response says how many were affected.
     */
    @PutMapping("/{id}/subjects")
    @Transactional
    public List<ClassroomSubjectResponse> replaceSubjects(@PathVariable Integer id,
            @Valid @RequestBody List<ClassroomSubjectRequest> requested) {
        privilegeService.requireUpdate(PrivilegeService.MODULE_CLASS);

        Classroom classroom = require(id);

        Set<Integer> seen = new HashSet<>();
        for (ClassroomSubjectRequest line : requested) {
            if (!seen.add(line.subjectId())) {
                throw ApiException.badRequest("The same subject was listed twice for " + label(classroom) + ".");
            }
        }

        List<ClassroomSubject> existing = classroomSubjectDao.listByClassroom(id);

        List<ClassroomSubject> removed = existing.stream()
                .filter(link -> !seen.contains(link.getSubject_detail_id().getId()))
                .toList();

        if (!removed.isEmpty()) {
            // student_subject points at these rows, so its rows go first.
            studentSubjectDao.deleteByClassroomSubjectIds(removed.stream().map(ClassroomSubject::getId).toList());
            classroomSubjectDao.deleteAll(removed);
        }

        List<ClassroomSubject> result = new ArrayList<>();
        for (ClassroomSubjectRequest line : requested) {
            ClassroomSubject link = existing.stream()
                    .filter(candidate -> Objects.equals(candidate.getSubject_detail_id().getId(), line.subjectId()))
                    .findFirst()
                    .orElseGet(ClassroomSubject::new);

            link.setClassroom_id(classroom);
            link.setSubject_detail_id(requireSubject(line.subjectId()));
            link.setEmployee_id(line.teacherId() == null ? null : requireEmployee(line.teacherId()));

            result.add(classroomSubjectDao.save(link));
        }

        return result.stream()
                .map(link -> ClassroomSubjectResponse.of(link, classroomSubjectDao.countStudents(link.getId())))
                .toList();
    }

    // ---- Roll ---------------------------------------------------------------

    @GetMapping("/{id}/students")
    public List<EnrolmentResponse> students(@PathVariable Integer id) {
        privilegeService.requireSelect(PrivilegeService.MODULE_CLASS);
        require(id);

        return registrationDao.listByClassroom(id).stream()
                .map(registration -> EnrolmentResponse.of(registration,
                        studentSubjectDao.listByRegistration(registration.getId()).stream()
                                .map(row -> ClassroomSubjectResponse.of(row.getClassroom_subject_id(), 0L))
                                .toList()))
                .toList();
    }

    // -------------------------------------------------------------------------

    private void apply(ClassroomRequest request, Classroom target, Integer selfId) {
        Grade grade = gradeDao.findById(request.gradeId())
                .orElseThrow(() -> ApiException.badRequest("Grade " + request.gradeId() + " does not exist."));

        // Omitting the year means the current one when creating, but "leave it
        // where it is" when editing. Resolving unconditionally would move an
        // existing 2025 class into 2026 the moment someone corrected its name
        // with the year picker on its default.
        AcademicYear year;
        if (request.academicYearId() != null) {
            year = academicYearService.resolve(request.academicYearId());
        } else if (target.getAcademic_year_id() != null) {
            year = target.getAcademic_year_id();
        } else {
            year = academicYearService.resolve(null);
        }

        String name = request.name().trim();

        Classroom clash = classroomDao.getByYearGradeAndName(year.getId(), grade.getId(), name);
        if (clash != null && !Objects.equals(clash.getId(), selfId)) {
            throw ApiException.conflict(
                    grade.getName() + " " + name + " already exists for " + year.getName() + ".");
        }

        target.setName(name);
        target.setGrade_id(grade);
        target.setAcademic_year_id(year);
        target.setEmployee_id(request.classTeacherId() == null ? null : requireEmployee(request.classTeacherId()));
        target.setMedium(request.medium() == null || request.medium().isBlank() ? null : request.medium().trim());
    }

    private Classroom require(Integer id) {
        return classroomDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Class " + id + " does not exist."));
    }

    private SubjectDetail requireSubject(Integer id) {
        return subjectDao.findById(id)
                .orElseThrow(() -> ApiException.badRequest("Subject " + id + " does not exist."));
    }

    private Employee requireEmployee(Integer id) {
        return employeeDao.findById(id)
                .orElseThrow(() -> ApiException.badRequest("Employee " + id + " does not exist."));
    }

    private String label(Classroom classroom) {
        String grade = classroom.getGrade_id() == null ? "" : classroom.getGrade_id().getName() + " ";
        return grade + classroom.getName();
    }
}
