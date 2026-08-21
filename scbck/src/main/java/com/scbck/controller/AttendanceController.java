package com.scbck.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.scbck.dto.AttendanceMarkResponse;
import com.scbck.dto.AttendanceSaveRequest;
import com.scbck.dto.AttendanceSheetResponse;
import com.scbck.dto.MessageResponse;
import com.scbck.dto.NamedRef;
import com.scbck.dto.StudentAttendanceSummary;
import com.scbck.exception.ApiException;
import com.scbck.model.Attendance;
import com.scbck.model.Classroom;
import com.scbck.model.Holiday;
import com.scbck.model.Student;
import com.scbck.model.StudentAttendance;
import com.scbck.model.StudentRegistration;
import com.scbck.repository.AttendanceDao;
import com.scbck.repository.HolidayDao;
import com.scbck.repository.ClassroomDao;
import com.scbck.repository.StudentAttendanceDao;
import com.scbck.repository.StudentRegistrationDao;
import com.scbck.service.AttendanceLetterPdfService;
import com.scbck.service.PrivilegeService;
import com.scbck.service.StudentAttendanceService;

import jakarta.validation.Valid;

/**
 * Attendance marking, one class and one day at a time.
 *
 * The unit of work here is a register page, not a mark: a class teacher goes
 * down the roll and saves once. {@link #sheet} returns that page whether or
 * not it has ever been saved, so the screen looks the same before the first
 * mark as after - the alternative, making the client create an empty register
 * first, means a half-created day survives every failure.
 *
 * The roll comes from the enrolments, so a student added to the class appears
 * on tomorrow's register without anything else being touched.
 */
@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceDao attendanceDao;
    private final StudentAttendanceDao markDao;
    private final ClassroomDao classroomDao;
    private final StudentRegistrationDao registrationDao;
    private final PrivilegeService privilegeService;
    private final HolidayDao holidayDao;
    private final StudentAttendanceService studentAttendanceService;
    private final AttendanceLetterPdfService letterPdfService;

    public AttendanceController(AttendanceDao attendanceDao, StudentAttendanceDao markDao,
            ClassroomDao classroomDao, StudentRegistrationDao registrationDao,
            PrivilegeService privilegeService, HolidayDao holidayDao,
            StudentAttendanceService studentAttendanceService,
            AttendanceLetterPdfService letterPdfService) {
        this.attendanceDao = attendanceDao;
        this.markDao = markDao;
        this.classroomDao = classroomDao;
        this.registrationDao = registrationDao;
        this.privilegeService = privilegeService;
        this.holidayDao = holidayDao;
        this.studentAttendanceService = studentAttendanceService;
        this.letterPdfService = letterPdfService;
    }

    /**
     * One student's attendance over a period, week by week.
     *
     * The counterpart to {@link #sheet}: the register answers "who was in
     * today", this answers "how has this child been attending", which is the
     * question the office is asked at the counter and the one the Ministry's
     * absence circular is about.
     */
    @GetMapping("/students/{studentId}")
    public StudentAttendanceSummary studentSummary(@PathVariable Integer studentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        privilegeService.requireSelect(PrivilegeService.MODULE_ATTENDANCE);
        return studentAttendanceService.summarise(studentId, from, to);
    }

    /**
     * One of the three attendance letters, as a PDF.
     *
     * The threshold is re-checked here rather than trusted from the caller: the
     * two absence notices are formal notices under Circular 53/2023, and one
     * sent to a family whose child does not meet the rule is worse than none.
     *
     * {@code meetingDate} and {@code meetingTime} are the only two things the
     * register cannot supply, so they are optional and print as the sample's
     * dotted lines when left out.
     */
    @GetMapping("/students/{studentId}/letter")
    public ResponseEntity<byte[]> letter(@PathVariable Integer studentId,
            @RequestParam String type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate meetingDate,
            @RequestParam(required = false) String meetingTime) {

        privilegeService.requireSelect(PrivilegeService.MODULE_ATTENDANCE);

        String letter = type == null ? "" : type.trim().toUpperCase();
        if (!List.of(StudentAttendanceService.LETTER_WEEK,
                StudentAttendanceService.LETTER_TWENTY_DAY,
                StudentAttendanceService.LETTER_FORTY_DAY).contains(letter)) {
            throw ApiException.badRequest("'" + type + "' is not one of the attendance letters.");
        }

        studentAttendanceService.requireLetter(studentId, letter);

        StudentAttendanceSummary summary = studentAttendanceService.summarise(studentId, from, to);
        byte[] body = letterPdfService.render(letter, summary, meetingDate, meetingTime);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(letterPdfService.fileNameFor(letter, summary))
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentLength(body.length)
                .body(body);
    }

    /** The register page for one class on one date. */
    @GetMapping
    public AttendanceSheetResponse sheet(@RequestParam Integer classroomId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        privilegeService.requireSelect(PrivilegeService.MODULE_ATTENDANCE);

        Classroom classroom = requireClassroom(classroomId);
        Attendance existing = attendanceDao.getByClassroomAndDate(classroomId, date);

        Map<Integer, Boolean> marks = new HashMap<>();
        if (existing != null) {
            for (StudentAttendance mark : markDao.listByAttendance(existing.getId())) {
                marks.put(mark.getStudent_id().getId(), mark.getAttendant());
            }
        }

        return toSheet(classroom, date, existing, marks);
    }

    /** Which days of a period already have a register, for the month view. */
    @GetMapping("/days")
    public List<LocalDate> markedDays(@RequestParam Integer classroomId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        privilegeService.requireSelect(PrivilegeService.MODULE_ATTENDANCE);
        requireClassroom(classroomId);

        return attendanceDao.listByClassroomBetween(classroomId, from, to).stream()
                .map(Attendance::getDate)
                .toList();
    }

    /**
     * Saves the whole register for one class and date.
     *
     * Idempotent by (class, date): saving again corrects the day rather than
     * adding a second register for it, which is what the unique constraint on
     * the table guarantees even if two teachers save at once.
     */
    @PutMapping
    @Transactional
    public AttendanceSheetResponse save(@Valid @RequestBody AttendanceSaveRequest request) {
        privilegeService.requireUpdate(PrivilegeService.MODULE_ATTENDANCE);

        Classroom classroom = requireClassroom(request.classroomId());
        privilegeService.requireClassTeacherOf(classroom, "mark this class's attendance");

        if (request.date().isAfter(LocalDate.now())) {
            throw ApiException.badRequest("Attendance cannot be marked for a date in the future.");
        }

        // Both attendance reports count a day as conducted purely because a
        // register exists for it, so a register opened on a holiday would show
        // up in every percentage as a day the whole class was absent.
        List<Holiday> holidays = holidayDao.findByDate(request.date());
        if (!holidays.isEmpty()) {
            throw ApiException.badRequest("School was not conducted on " + request.date()
                    + " — " + holidays.get(0).getName()
                    + ". Remove the holiday first if school was in fact held.");
        }

        List<Student> roll = rollOf(classroom.getId());
        Set<Integer> onRoll = new HashSet<>(roll.stream().map(Student::getId).toList());

        Map<Integer, Boolean> wanted = new HashMap<>();
        for (AttendanceSaveRequest.Mark mark : request.marks()) {
            if (!onRoll.contains(mark.studentId())) {
                throw ApiException.badRequest("Student " + mark.studentId() + " is not on the roll of "
                        + label(classroom) + ".");
            }
            wanted.put(mark.studentId(), mark.present());
        }

        Attendance register = attendanceDao.getByClassroomAndDate(classroom.getId(), request.date());
        if (register == null) {
            register = new Attendance();
            register.setClassroom_id(classroom);
            register.setDate(request.date());
            register = attendanceDao.save(register);
        }

        // Rewritten wholesale: a student left out of the payload has their mark
        // cleared, so a correction cannot leave yesterday's answer behind.
        markDao.deleteByAttendance(register.getId());

        List<StudentAttendance> rows = new ArrayList<>();
        for (Student student : roll) {
            Boolean present = wanted.get(student.getId());
            if (present == null) {
                continue;
            }
            StudentAttendance row = new StudentAttendance();
            row.setAttendence_id(register);
            row.setStudent_id(student);
            row.setAttendant(present);
            rows.add(row);
        }
        markDao.saveAll(rows);

        // The ER model's day totals, recomputed from the marks just written so
        // they can never disagree with them. No report reads them.
        int present = (int) rows.stream().filter(StudentAttendance::getAttendant).count();
        register.setTotal_present(present);
        register.setTotal_abscent(rows.size() - present);
        register.setTotal_child_count(roll.size());
        Attendance saved = attendanceDao.save(register);

        return toSheet(classroom, request.date(), saved, wanted);
    }

    /** Removes a day's register - use when school was not in fact conducted. */
    @DeleteMapping("/{id}")
    @Transactional
    public MessageResponse delete(@PathVariable Integer id) {
        privilegeService.requireDelete(PrivilegeService.MODULE_ATTENDANCE);

        Attendance register = attendanceDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Attendance register " + id + " does not exist."));

        privilegeService.requireClassTeacherOf(register.getClassroom_id(),
                "remove this class's register");

        String description = label(register.getClassroom_id()) + " on " + register.getDate();

        markDao.deleteByAttendance(id);
        attendanceDao.delete(register);

        // The day now counts as "school not conducted" everywhere, because that
        // is what the absence of a register means.
        return MessageResponse.of("Register removed for " + description + ".");
    }

    // -------------------------------------------------------------------------

    private AttendanceSheetResponse toSheet(Classroom classroom, LocalDate date,
            Attendance register, Map<Integer, Boolean> marks) {

        List<Student> roll = rollOf(classroom.getId());

        List<AttendanceMarkResponse> students = roll.stream()
                .map(student -> new AttendanceMarkResponse(
                        student.getId(),
                        student.getStu_no(),
                        student.getFullname(),
                        marks.get(student.getId())))
                .toList();

        int present = (int) students.stream().filter(row -> Boolean.TRUE.equals(row.present())).count();
        int absent = (int) students.stream().filter(row -> Boolean.FALSE.equals(row.present())).count();

        var grade = classroom.getGrade_id();
        var year = classroom.getAcademic_year_id();
        var teacher = classroom.getEmployee_id();

        return new AttendanceSheetResponse(
                register == null ? null : register.getId(),
                date,
                NamedRef.of(classroom.getId(), classroom.getName()),
                grade == null ? null : NamedRef.of(grade.getId(), grade.getName()),
                year == null ? null : NamedRef.of(year.getId(), year.getName()),
                teacher == null ? null : NamedRef.of(teacher.getId(), teacher.getFullname()),
                register != null,
                present,
                absent,
                students.size() - present - absent,
                students.size(),
                students);
    }

    /**
     * The students a register should list: actively enrolled, not deleted, in
     * admission-number order so the page reads the same way every day.
     */
    private List<Student> rollOf(Integer classroomId) {
        return registrationDao.listByClassroom(classroomId).stream()
                .filter(this::isLive)
                .map(StudentRegistration::getStudent_id)
                .sorted((left, right) -> nullSafe(left.getStu_no()).compareTo(nullSafe(right.getStu_no())))
                .toList();
    }

    /** Same filter the count reports use, so a roll and a head count agree. */
    private boolean isLive(StudentRegistration registration) {
        var status = registration.getRegistration_status_id();
        if (status != null && !"active".equalsIgnoreCase(status.getName())) {
            return false;
        }
        var studentStatus = registration.getStudent_id().getStudent_status_id();
        return studentStatus == null || !"deleted".equalsIgnoreCase(studentStatus.getName());
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private Classroom requireClassroom(Integer id) {
        return classroomDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Class " + id + " does not exist."));
    }

    private String label(Classroom classroom) {
        String grade = classroom.getGrade_id() == null ? "" : classroom.getGrade_id().getName() + " ";
        return grade + classroom.getName();
    }
}
