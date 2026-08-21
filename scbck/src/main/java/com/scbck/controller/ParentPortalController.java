package com.scbck.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.scbck.dto.FeePosition;
import com.scbck.dto.MarkSheet;
import com.scbck.dto.ParentChild;
import com.scbck.dto.StudentAttendanceSummary;
import com.scbck.exception.ApiException;
import com.scbck.model.Classroom;
import com.scbck.model.Student;
import com.scbck.model.StudentRegistration;
import com.scbck.model.Term;
import com.scbck.model.User;
import com.scbck.repository.StudentDao;
import com.scbck.repository.StudentRegistrationDao;
import com.scbck.repository.TermDao;
import com.scbck.repository.UserDao;
import com.scbck.service.FeeService;
import com.scbck.service.MarkSheetService;
import com.scbck.service.PrivilegeService;
import com.scbck.service.StudentAttendanceService;

/**
 * What a parent may see: their own children, and nothing else.
 *
 * The access rule is the point of this controller existing at all. Every other
 * module answers "may this user read Students?" - a yes-or-no question about a
 * whole table. A parent's answer is "yes, for these three rows", which no
 * privilege module can express, so it is enforced structurally instead: the
 * child list is derived from the guardian on the caller's own account, and
 * every endpoint resolves the requested student through {@link #requireChild}
 * rather than trusting the id in the URL.
 *
 * That means a parent who edits the student id in the address bar gets a 403,
 * not somebody else's child - which is the failure mode a portal like this has
 * to be built to survive.
 */
@RestController
@RequestMapping("/api/parent")
public class ParentPortalController {

    private final UserDao userDao;
    private final StudentDao studentDao;
    private final StudentRegistrationDao registrationDao;
    private final TermDao termDao;
    private final MarkSheetService markSheetService;
    private final StudentAttendanceService attendanceService;
    private final FeeService feeService;
    private final PrivilegeService privilegeService;

    public ParentPortalController(UserDao userDao, StudentDao studentDao,
            StudentRegistrationDao registrationDao, TermDao termDao,
            MarkSheetService markSheetService, StudentAttendanceService attendanceService,
            FeeService feeService, PrivilegeService privilegeService) {
        this.userDao = userDao;
        this.studentDao = studentDao;
        this.registrationDao = registrationDao;
        this.termDao = termDao;
        this.markSheetService = markSheetService;
        this.attendanceService = attendanceService;
        this.feeService = feeService;
        this.privilegeService = privilegeService;
    }

    /** The children linked to the signed-in parent's guardian record. */
    @GetMapping("/children")
    public List<ParentChild> children() {
        return childrenOf().stream().map(this::toChild).toList();
    }

    /**
     * The terms one child has marks for, newest first.
     *
     * Offered as a list rather than making the parent guess: a term with no
     * marks entered yet is not shown, so the portal never presents an empty
     * sheet as though the child had scored nothing.
     */
    @GetMapping("/children/{studentId}/terms")
    public List<ParentChild.TermMarks> terms(@PathVariable Integer studentId) {
        Student child = requireChild(studentId);

        List<ParentChild.TermMarks> results = new ArrayList<>();
        for (StudentRegistration enrolment : registrationDao.listByStudent(child.getId())) {
            Classroom classroom = enrolment.getClassroom_id();
            if (classroom == null || classroom.getAcademic_year_id() == null) {
                continue;
            }

            for (Term term : termDao.listByAcademicYear(classroom.getAcademic_year_id().getId())) {
                ParentChild.TermMarks marks = marksOf(child, classroom, term);
                if (marks != null && !marks.subjects().isEmpty()) {
                    results.add(marks);
                }
            }
        }

        // Newest first: the term a parent wants is almost always the last one.
        return results.reversed();
    }

    /** One child's marks for one term. */
    @GetMapping("/children/{studentId}/marks")
    public ParentChild.TermMarks marks(@PathVariable Integer studentId,
            @RequestParam Integer termId) {

        Student child = requireChild(studentId);
        Term term = termDao.findById(termId)
                .orElseThrow(() -> ApiException.notFound("Term " + termId + " does not exist."));

        Classroom classroom = classroomFor(child, term);
        if (classroom == null) {
            throw ApiException.notFound(
                    child.getFullname() + " was not enrolled in a class during " + term.getName() + ".");
        }

        ParentChild.TermMarks marks = marksOf(child, classroom, term);
        if (marks == null) {
            throw ApiException.notFound("No marks have been entered for that term yet.");
        }
        return marks;
    }

    /** One child's attendance over a period. */
    @GetMapping("/children/{studentId}/attendance")
    public StudentAttendanceSummary attendance(@PathVariable Integer studentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        Student child = requireChild(studentId);
        return attendanceService.summarise(child.getId(), from, to);
    }

    /**
     * One child's fee position and receipts.
     *
     * Included because it is the other thing a parent telephones the office
     * about, and because they are entitled to a statement of money they
     * themselves have paid.
     */
    @GetMapping("/children/{studentId}/payments")
    public FeePosition payments(@PathVariable Integer studentId,
            @RequestParam(required = false) Integer academicYearId) {

        Student child = requireChild(studentId);
        return feeService.positionOf(child.getId(), academicYearId);
    }

    // ---- Access control -----------------------------------------------------

    /**
     * The children of the guardian on the signed-in account.
     *
     * A staff account reaching this controller is refused rather than shown
     * everything: the portal is a different view of the school, not a reduced
     * one, and a member of staff who wants a student's marks has the Marks
     * screen.
     */
    private List<Student> childrenOf() {
        User account = userDao.getByUsername(privilegeService.currentUsername());

        if (account == null || account.getGuardian_id() == null) {
            throw ApiException.forbidden(
                    "This area is for parent accounts. Your account is not linked to a guardian record.");
        }

        return studentDao.listByGuardian(account.getGuardian_id().getId());
    }

    /** Resolves a student id against the caller's own children, or refuses. */
    private Student requireChild(Integer studentId) {
        return childrenOf().stream()
                .filter(child -> Objects.equals(child.getId(), studentId))
                .findFirst()
                .orElseThrow(() -> ApiException.forbidden(
                        "You may only view the records of your own children."));
    }

    // ---- Assembly -----------------------------------------------------------

    private ParentChild toChild(Student student) {
        Classroom classroom = currentClassroom(student);
        String grade = classroom != null && classroom.getGrade_id() != null
                ? classroom.getGrade_id().getName()
                : student.getGrade_id() == null ? null : student.getGrade_id().getName();

        return new ParentChild(
                student.getId(),
                student.getStu_no(),
                student.getFullname(),
                classroom == null ? null : ((grade == null ? "" : grade + " ") + classroom.getName()),
                grade,
                student.getDob());
    }

    private Classroom currentClassroom(Student student) {
        List<StudentRegistration> history = registrationDao.listByStudent(student.getId());
        return history.isEmpty() ? null : history.get(0).getClassroom_id();
    }

    /** The class the child sat in during the year that term belongs to. */
    private Classroom classroomFor(Student child, Term term) {
        if (term.getAcademic_year_id() == null) {
            return null;
        }
        Integer yearId = term.getAcademic_year_id().getId();

        return registrationDao.listByStudent(child.getId()).stream()
                .map(StudentRegistration::getClassroom_id)
                .filter(Objects::nonNull)
                .filter(classroom -> classroom.getAcademic_year_id() != null
                        && classroom.getAcademic_year_id().getId().equals(yearId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Pulls one child's line out of the class mark sheet.
     *
     * Built from the same {@link MarkSheetService} the Marks screen uses rather
     * than from a query of its own, so a parent and a class teacher looking at
     * the same term see the same marks, the same average and the same rank. The
     * rest of the sheet - every other child's line - is dropped here and never
     * leaves the server.
     */
    private ParentChild.TermMarks marksOf(Student child, Classroom classroom, Term term) {
        MarkSheet sheet;
        try {
            sheet = markSheetService.build(classroom.getId(), term.getId());
        } catch (RuntimeException notAvailable) {
            // A class with no timetable or no term set up is not an error to a
            // parent; it simply has nothing to show yet.
            return null;
        }

        MarkSheet.Row row = sheet.rows().stream()
                .filter(candidate -> Objects.equals(candidate.studentId(), child.getId()))
                .findFirst()
                .orElse(null);

        if (row == null) {
            return null;
        }

        List<ParentChild.SubjectMark> subjects = new ArrayList<>();
        for (int index = 0; index < sheet.subjects().size() && index < row.cells().size(); index++) {
            MarkSheet.Subject subject = sheet.subjects().get(index);
            MarkSheet.Cell cell = row.cells().get(index);

            // Subjects the child does not take are left out rather than shown
            // as a dash: a parent reading a list of thirteen subjects when
            // their child takes nine would reasonably think marks were missing.
            if (!cell.enrolled()) {
                continue;
            }

            subjects.add(new ParentChild.SubjectMark(
                    subject.name(),
                    subject.categoryName(),
                    cell.marks(),
                    cell.grade()));
        }

        return new ParentChild.TermMarks(
                term.getId(),
                term.getName(),
                sheet.academicYear(),
                subjects,
                row.total(),
                row.average(),
                row.rank(),
                sheet.rows().size());
    }
}
