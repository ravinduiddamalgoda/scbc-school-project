package com.scbck.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scbck.dto.StudentAttendanceSummary;
import com.scbck.exception.ApiException;
import com.scbck.model.Classroom;
import com.scbck.model.Student;
import com.scbck.model.StudentAttendance;
import com.scbck.model.StudentRegistration;
import com.scbck.repository.StudentAttendanceDao;
import com.scbck.repository.StudentDao;
import com.scbck.repository.StudentRegistrationDao;

/**
 * Attendance seen one student at a time, and the rule deciding which letter the
 * school may send.
 *
 * The register is marked class by class, which answers "who was in today" but
 * not "how has this child been attending" - the question the office is actually
 * asked, and the one the Ministry's absence circular is about. Answering it
 * meant opening a month of register pages and counting by eye.
 *
 * The three thresholds live here, in one method, because they are the whole
 * point: a letter is a formal notice under Circular 53/2023 and must not go out
 * to a family whose child does not meet the rule. The letter endpoints re-check
 * against this same method rather than trusting a flag from the browser.
 */
@Service
public class StudentAttendanceService {

    /** Continuous absences that justify the first notice. */
    public static final int TWENTY_DAY_THRESHOLD = 20;

    /** Continuous absences that justify treating the student as having left. */
    public static final int FORTY_DAY_THRESHOLD = 40;

    public static final String LETTER_WEEK = "WEEK";
    public static final String LETTER_TWENTY_DAY = "TWENTY_DAY";
    public static final String LETTER_FORTY_DAY = "FORTY_DAY";

    /**
     * A date earlier than any register the school will ever hold.
     *
     * {@code LocalDate.MIN} is year -999999999, which MySQL's DATE column
     * cannot represent and the driver rejects rather than treating as "no lower
     * bound".
     */
    private static final LocalDate BEGINNING = LocalDate.of(1900, 1, 1);

    private final StudentDao studentDao;
    private final StudentAttendanceDao markDao;
    private final StudentRegistrationDao registrationDao;

    public StudentAttendanceService(StudentDao studentDao, StudentAttendanceDao markDao,
            StudentRegistrationDao registrationDao) {
        this.studentDao = studentDao;
        this.markDao = markDao;
        this.registrationDao = registrationDao;
    }

    /**
     * One student's attendance between two dates, week by week.
     *
     * @param from inclusive; @param to inclusive.
     */
    @Transactional(readOnly = true)
    public StudentAttendanceSummary summarise(Integer studentId, LocalDate from, LocalDate to) {
        Student student = studentDao.findById(studentId)
                .orElseThrow(() -> ApiException.notFound("Student " + studentId + " does not exist."));

        if (from == null || to == null) {
            throw ApiException.badRequest("A date range is required.");
        }
        if (to.isBefore(from)) {
            throw ApiException.badRequest("The end of the range is before its start.");
        }

        List<StudentAttendance> marks = markDao.listByStudentBetween(studentId, from, to);

        int present = (int) marks.stream()
                .filter(mark -> Boolean.TRUE.equals(mark.getAttendant()))
                .count();
        int absent = marks.size() - present;

        List<StudentAttendanceSummary.Week> weeks = weeksOf(from, to, marks);

        // The run is computed over all of the student's history, not only the
        // window on screen: a child absent since March is absent since March
        // whichever month the office happens to be looking at.
        Run run = currentAbsenceRun(studentId);

        return new StudentAttendanceSummary(
                student.getId(),
                student.getStu_no(),
                student.getFullname(),
                classNameOf(studentId),
                student.getGuardian_id() == null ? null : student.getGuardian_id().getFullname(),
                from,
                to,
                marks.size(),
                present,
                absent,
                percentage(present, marks.size()),
                weeks,
                run.length(),
                run.since(),
                run.lastMarked(),
                lettersFor(marks.size(), run.length()));
    }

    /**
     * Which letters the record justifies.
     *
     * The week letter needs a week that was actually marked - sending a
     * summary of nothing is worse than sending none. The two absence letters
     * are about a continuous run, per Circular 53/2023, so a student absent
     * twenty days spread over a term does not qualify and a student absent
     * twenty in a row does.
     *
     * At forty days both are offered: the school normally sends the twenty-day
     * notice first, and a run that reached forty without one having gone out
     * still needs it on file.
     */
    public List<String> lettersFor(int daysMarked, int consecutiveAbsent) {
        List<String> letters = new ArrayList<>();
        if (daysMarked > 0) {
            letters.add(LETTER_WEEK);
        }
        if (consecutiveAbsent >= TWENTY_DAY_THRESHOLD) {
            letters.add(LETTER_TWENTY_DAY);
        }
        if (consecutiveAbsent >= FORTY_DAY_THRESHOLD) {
            letters.add(LETTER_FORTY_DAY);
        }
        return letters;
    }

    /** Guards a letter endpoint: throws unless the record justifies it. */
    @Transactional(readOnly = true)
    public void requireLetter(Integer studentId, String letter) {
        Run run = currentAbsenceRun(studentId);

        if (LETTER_TWENTY_DAY.equals(letter) && run.length() < TWENTY_DAY_THRESHOLD) {
            throw ApiException.badRequest(
                    "This student has been continuously absent for " + run.length()
                            + " school day(s). The twenty-day notice applies from "
                            + TWENTY_DAY_THRESHOLD + ".");
        }
        if (LETTER_FORTY_DAY.equals(letter) && run.length() < FORTY_DAY_THRESHOLD) {
            throw ApiException.badRequest(
                    "This student has been continuously absent for " + run.length()
                            + " school day(s). The forty-day notice applies from "
                            + FORTY_DAY_THRESHOLD + ".");
        }
    }

    /**
     * The unbroken run of absences ending at the student's most recent mark.
     *
     * Counted in school days, not calendar days: a day with no register is a
     * day school was not conducted for that class, and counting weekends and
     * holidays towards a Ministry threshold would put the school over it a
     * fortnight early.
     */
    @Transactional(readOnly = true)
    public Run currentAbsenceRun(Integer studentId) {
        List<StudentAttendance> marks = markDao.listByStudentFrom(studentId, BEGINNING);

        int length = 0;
        LocalDate since = null;
        LocalDate lastMarked = marks.isEmpty()
                ? null
                : marks.get(marks.size() - 1).getAttendence_id().getDate();

        for (int index = marks.size() - 1; index >= 0; index--) {
            StudentAttendance mark = marks.get(index);
            if (Boolean.TRUE.equals(mark.getAttendant())) {
                break;
            }
            length++;
            since = mark.getAttendence_id().getDate();
        }

        return new Run(length, since, lastMarked);
    }

    /** An unbroken run of absences: how many, since when, and up to when. */
    public record Run(int length, LocalDate since, LocalDate lastMarked) {
    }

    // -------------------------------------------------------------------------

    /**
     * Splits the period into Monday-to-Sunday weeks and totals each.
     *
     * Weeks are numbered within the period rather than within the year, which
     * is how the school's own monthly register sheet reads - Week 1 to Week 5
     * down the side of the month.
     */
    private List<StudentAttendanceSummary.Week> weeksOf(LocalDate from, LocalDate to,
            List<StudentAttendance> marks) {

        List<StudentAttendanceSummary.Week> weeks = new ArrayList<>();

        LocalDate cursor = from;
        int number = 1;
        while (!cursor.isAfter(to)) {
            LocalDate weekEnd = cursor.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            LocalDate windowEnd = weekEnd.isAfter(to) ? to : weekEnd;

            final LocalDate start = cursor;
            List<StudentAttendance> inWeek = marks.stream()
                    .filter(mark -> {
                        LocalDate date = mark.getAttendence_id().getDate();
                        return !date.isBefore(start) && !date.isAfter(windowEnd);
                    })
                    .toList();

            int present = (int) inWeek.stream()
                    .filter(mark -> Boolean.TRUE.equals(mark.getAttendant()))
                    .count();

            weeks.add(new StudentAttendanceSummary.Week(
                    number, start, windowEnd, inWeek.size(), present, inWeek.size() - present));

            cursor = windowEnd.plusDays(1);
            number++;
        }

        return weeks;
    }

    /** The class the student is currently enrolled in, e.g. "Grade 11 B". */
    private String classNameOf(Integer studentId) {
        List<StudentRegistration> history = registrationDao.listByStudent(studentId);
        if (history.isEmpty()) {
            return null;
        }
        Classroom classroom = history.get(0).getClassroom_id();
        if (classroom == null) {
            return null;
        }
        String grade = classroom.getGrade_id() == null ? "" : classroom.getGrade_id().getName() + " ";
        return (grade + classroom.getName()).trim();
    }

    private static double percentage(int part, int whole) {
        if (whole == 0) {
            return 0d;
        }
        return Math.round(part * 1000d / whole) / 10d;
    }
}
