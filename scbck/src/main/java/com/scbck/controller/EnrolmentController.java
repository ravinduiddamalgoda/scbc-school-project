package com.scbck.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

import com.scbck.dto.ClassroomSubjectResponse;
import com.scbck.dto.EnrolmentRequest;
import com.scbck.dto.EnrolmentResponse;
import com.scbck.dto.MessageResponse;
import com.scbck.exception.ApiException;
import com.scbck.model.Classroom;
import com.scbck.model.ClassroomSubject;
import com.scbck.model.RegistrationStatus;
import com.scbck.model.Student;
import com.scbck.model.StudentRegistration;
import com.scbck.model.GradeSubject;
import com.scbck.model.StudentSubject;
import com.scbck.model.SubjectDetail;
import com.scbck.repository.ClassroomDao;
import com.scbck.repository.ClassroomSubjectDao;
import com.scbck.repository.RegistrationStatusDao;
import com.scbck.repository.StudentDao;
import com.scbck.repository.StudentRegistrationDao;
import com.scbck.repository.GradeSubjectDao;
import com.scbck.repository.StudentSubjectDao;
import com.scbck.service.PrivilegeService;

import jakarta.validation.Valid;

/**
 * Enrolments: which class a student sits in, and which of that class's
 * subjects they take.
 *
 * Gated on the Student module rather than Class - placing a child is a
 * registrar's job, and the same people who admit students do it.
 */
@RestController
@RequestMapping("/api/enrolments")
public class EnrolmentController {

    private static final String STATUS_ACTIVE = "Active";

    private final StudentRegistrationDao registrationDao;
    private final StudentSubjectDao studentSubjectDao;
    private final ClassroomSubjectDao classroomSubjectDao;
    private final ClassroomDao classroomDao;
    private final StudentDao studentDao;
    private final RegistrationStatusDao registrationStatusDao;
    private final GradeSubjectDao gradeSubjectDao;
    private final PrivilegeService privilegeService;

    public EnrolmentController(StudentRegistrationDao registrationDao, StudentSubjectDao studentSubjectDao,
            ClassroomSubjectDao classroomSubjectDao, ClassroomDao classroomDao, StudentDao studentDao,
            RegistrationStatusDao registrationStatusDao, GradeSubjectDao gradeSubjectDao,
            PrivilegeService privilegeService) {
        this.registrationDao = registrationDao;
        this.studentSubjectDao = studentSubjectDao;
        this.classroomSubjectDao = classroomSubjectDao;
        this.classroomDao = classroomDao;
        this.studentDao = studentDao;
        this.registrationStatusDao = registrationStatusDao;
        this.gradeSubjectDao = gradeSubjectDao;
        this.privilegeService = privilegeService;
    }

    /** Every enrolment, or one student's history when studentId is supplied. */
    @GetMapping
    public List<EnrolmentResponse> findAll(@RequestParam(required = false) Integer studentId) {
        privilegeService.requireSelect(PrivilegeService.MODULE_STUDENT);

        List<StudentRegistration> registrations = studentId == null
                ? registrationDao.findAll()
                : registrationDao.listByStudent(studentId);

        return registrations.stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public EnrolmentResponse findById(@PathVariable Integer id) {
        privilegeService.requireSelect(PrivilegeService.MODULE_STUDENT);
        return toResponse(require(id));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<EnrolmentResponse> create(@Valid @RequestBody EnrolmentRequest request) {
        privilegeService.requireInsert(PrivilegeService.MODULE_STUDENT);

        Student student = requireStudent(request.studentId());
        Classroom classroom = requireClassroom(request.classroomId());

        StudentRegistration clash = registrationDao.getByStudentAndClassroom(student.getId(), classroom.getId());
        if (clash != null) {
            throw ApiException.conflict(student.getFullname() + " is already enrolled in " + label(classroom) + ".");
        }

        StudentRegistration registration = new StudentRegistration();
        registration.setStudent_id(student);
        registration.setClassroom_id(classroom);
        registration.setReg_no(String.format("%010d", registrationDao.nextRegSequence()));
        registration.setDate(request.date() == null ? LocalDate.now() : request.date());
        registration.setTotal_fee(request.totalFee());
        registration.setRegistration_status_id(resolveStatus(request.registrationStatusId()));

        StudentRegistration saved = registrationDao.save(registration);
        replaceSubjects(saved, request.classroomSubjectIds());

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @PutMapping("/{id}")
    @Transactional
    public EnrolmentResponse update(@PathVariable Integer id, @Valid @RequestBody EnrolmentRequest request) {
        privilegeService.requireUpdate(PrivilegeService.MODULE_STUDENT);

        StudentRegistration existing = require(id);
        Student student = requireStudent(request.studentId());
        Classroom classroom = requireClassroom(request.classroomId());

        StudentRegistration clash = registrationDao.getByStudentAndClassroom(student.getId(), classroom.getId());
        if (clash != null && !Objects.equals(clash.getId(), id)) {
            throw ApiException.conflict(student.getFullname() + " is already enrolled in " + label(classroom) + ".");
        }

        // Moving to a different class invalidates the old subject choices - they
        // belong to the class that was left behind, not to the one being joined.
        boolean movedClass = !Objects.equals(existing.getClassroom_id().getId(), classroom.getId());

        existing.setStudent_id(student);
        existing.setClassroom_id(classroom);
        existing.setDate(request.date() == null ? existing.getDate() : request.date());
        existing.setTotal_fee(request.totalFee());
        existing.setRegistration_status_id(resolveStatus(request.registrationStatusId()));

        StudentRegistration saved = registrationDao.save(existing);

        if (movedClass && request.classroomSubjectIds() == null) {
            // The caller said nothing about subjects, but the ones on file point
            // at the timetable of the class just left, so they cannot stand.
            studentSubjectDao.deleteByRegistration(saved.getId());
        } else {
            replaceSubjects(saved, request.classroomSubjectIds());
        }

        return toResponse(saved);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public MessageResponse delete(@PathVariable Integer id) {
        privilegeService.requireDelete(PrivilegeService.MODULE_STUDENT);

        StudentRegistration existing = require(id);
        String description = existing.getStudent_id().getFullname() + " / " + label(existing.getClassroom_id());

        studentSubjectDao.deleteByRegistration(id);
        registrationDao.delete(existing);

        return MessageResponse.of("Enrolment removed: " + description + ".");
    }

    // -------------------------------------------------------------------------

    /**
     * Rewrites a student's subject choices.
     *
     * Every id has to be a timetable line of the class the student is actually
     * in; anything else is rejected rather than silently dropped, because a
     * subject quietly missing from the roll would show up as an unexplained
     * count in the report weeks later.
     */
    private void replaceSubjects(StudentRegistration registration, List<Integer> classroomSubjectIds) {
        if (classroomSubjectIds == null) {
            return;
        }

        studentSubjectDao.deleteByRegistration(registration.getId());

        if (classroomSubjectIds.isEmpty()) {
            return;
        }

        Set<Integer> wanted = new LinkedHashSet<>(classroomSubjectIds);
        List<ClassroomSubject> offered = classroomSubjectDao.listByClassroom(registration.getClassroom_id().getId());

        List<StudentSubject> rows = new ArrayList<>();
        for (Integer wantedId : wanted) {
            ClassroomSubject line = offered.stream()
                    .filter(candidate -> Objects.equals(candidate.getId(), wantedId))
                    .findFirst()
                    .orElseThrow(() -> ApiException.badRequest(
                            "Subject " + wantedId + " is not on the timetable of "
                                    + label(registration.getClassroom_id()) + "."));

            StudentSubject row = new StudentSubject();
            row.setStudent_registration_id(registration);
            row.setClassroom_subject_id(line);
            rows.add(row);
        }

        assertBasketsRespected(registration, rows);

        studentSubjectDao.saveAll(rows);
    }

    /**
     * Refuses a set of subjects that breaks the grade's category rules.
     *
     * The optional baskets are pick-one: a grade 12 candidate takes one subject
     * from Category 3, not ICT and Chemistry both. Nothing had ever checked it,
     * and the consequence was quiet rather than loud - the average is the total
     * over the subjects recorded, so a student carrying a sixth subject was
     * ranked against classmates on a different divisor, and the sheet gave no
     * hint that anything was wrong.
     *
     * Only baskets the curriculum actually constrains are checked. A grade with
     * no curriculum recorded, or a basket with no expected count, is left alone
     * rather than guessed at.
     */
    private void assertBasketsRespected(StudentRegistration registration, List<StudentSubject> rows) {
        Classroom classroom = registration.getClassroom_id();
        if (classroom == null || classroom.getGrade_id() == null) {
            return;
        }

        List<GradeSubject> curriculum = gradeSubjectDao.listForGrade(classroom.getGrade_id().getId());
        if (curriculum.isEmpty()) {
            return;
        }

        Map<Integer, String> basketBySubject = new LinkedHashMap<>();
        for (GradeSubject entry : curriculum) {
            basketBySubject.put(entry.getSubject().getId(), entry.getBasket());
        }

        Map<String, List<String>> chosenByBasket = new LinkedHashMap<>();
        for (StudentSubject row : rows) {
            SubjectDetail subject = row.getClassroom_subject_id().getSubject_detail_id();
            String basket = basketBySubject.get(subject.getId());

            // Core and General are "everyone takes them", so more than one is
            // the point rather than a mistake.
            if (basket == null
                    || GradeSubject.CORE.equals(basket)
                    || GradeSubject.GENERAL.equals(basket)) {
                continue;
            }
            chosenByBasket.computeIfAbsent(basket, key -> new ArrayList<>()).add(subject.getName());
        }

        for (Map.Entry<String, List<String>> entry : chosenByBasket.entrySet()) {
            if (entry.getValue().size() > 1) {
                throw ApiException.badRequest(
                        classroom.getGrade_id().getName() + " students take one subject from "
                                + entry.getKey() + ", but " + String.join(" and ", entry.getValue())
                                + " are both selected. Choose one.");
            }
        }
    }

    private EnrolmentResponse toResponse(StudentRegistration registration) {
        List<ClassroomSubjectResponse> subjects = studentSubjectDao.listByRegistration(registration.getId()).stream()
                .map(row -> ClassroomSubjectResponse.of(row.getClassroom_subject_id(), 0L))
                .toList();

        return EnrolmentResponse.of(registration, subjects);
    }

    /** Defaults to "Active" so a count report never has to guess. */
    private RegistrationStatus resolveStatus(Integer statusId) {
        if (statusId != null) {
            return registrationStatusDao.findById(statusId)
                    .orElseThrow(() -> ApiException.badRequest("Registration status " + statusId + " does not exist."));
        }
        return registrationStatusDao.getByName(STATUS_ACTIVE);
    }

    private StudentRegistration require(Integer id) {
        return registrationDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Enrolment " + id + " does not exist."));
    }

    private Student requireStudent(Integer id) {
        return studentDao.findById(id)
                .orElseThrow(() -> ApiException.badRequest("Student " + id + " does not exist."));
    }

    private Classroom requireClassroom(Integer id) {
        return classroomDao.findById(id)
                .orElseThrow(() -> ApiException.badRequest("Class " + id + " does not exist."));
    }

    private String label(Classroom classroom) {
        String grade = classroom.getGrade_id() == null ? "" : classroom.getGrade_id().getName() + " ";
        return grade + classroom.getName();
    }
}
