package com.scbck.service;

import static com.scbck.service.ReportLayout.classLabel;
import static com.scbck.service.ReportLayout.document;
import static com.scbck.service.ReportLayout.percentage;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.scbck.dto.ReportColumn;
import com.scbck.dto.ReportDocument;
import com.scbck.dto.ReportRequest;
import com.scbck.dto.ReportSection;
import com.scbck.exception.ApiException;
import com.scbck.model.AcademicYear;
import com.scbck.model.Classroom;
import com.scbck.model.Student;
import com.scbck.model.StudentAttendance;
import com.scbck.model.StudentRegistration;
import com.scbck.model.Term;
import com.scbck.repository.AttendanceDao;
import com.scbck.repository.ClassroomDao;
import com.scbck.repository.StudentAttendanceDao;
import com.scbck.repository.StudentRegistrationDao;
import com.scbck.repository.TermDao;
import com.scbck.repository.projection.CountByKey;

/**
 * The two attendance reports: the month register a class teacher keeps, and
 * the term summary the office reads.
 *
 * Both derive "days school was conducted" from the existence of a register
 * rather than from a school calendar, because that is the only definition that
 * cannot go stale - a day nobody marked was a day nobody held.
 */
@Service
public class AttendanceReports {

    private static final DateTimeFormatter MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM yyyy");

    private static final LocalDate EARLIEST = LocalDate.of(1900, 1, 1);
    private static final LocalDate LATEST = LocalDate.of(2999, 12, 31);

    private final AttendanceDao attendanceDao;
    private final StudentAttendanceDao markDao;
    private final StudentRegistrationDao registrationDao;
    private final ClassroomDao classroomDao;
    private final TermDao termDao;

    public AttendanceReports(AttendanceDao attendanceDao, StudentAttendanceDao markDao,
            StudentRegistrationDao registrationDao, ClassroomDao classroomDao, TermDao termDao) {
        this.attendanceDao = attendanceDao;
        this.markDao = markDao;
        this.registrationDao = registrationDao;
        this.classroomDao = classroomDao;
        this.termDao = termDao;
    }

    // ---- Monthly register ---------------------------------------------------

    /**
     * The register page itself: students down the side, school days across the
     * top, 1 for present and 0 for absent, with per-week and monthly totals.
     *
     * Only days that have a register get a column, so a month prints at its
     * real width instead of padding out to 31 and leaving the reader to work
     * out which columns were weekends.
     */
    public ReportDocument register(ReportRequest request, AcademicYear year) {
        Classroom classroom = requireClassroom(request.requireClassroomId());
        YearMonth month = request.monthOrCurrent();

        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();

        List<LocalDate> days = attendanceDao.listByClassroomBetween(classroom.getId(), from, to).stream()
                .map(register -> register.getDate())
                .toList();

        // student id -> date -> present
        Map<Integer, Map<LocalDate, Boolean>> marks = new LinkedHashMap<>();
        Map<Integer, Student> students = new LinkedHashMap<>();

        for (StudentAttendance mark : markDao.listByClassroomBetween(classroom.getId(), from, to)) {
            Student student = mark.getStudent_id();
            students.putIfAbsent(student.getId(), student);
            marks.computeIfAbsent(student.getId(), key -> new LinkedHashMap<>())
                    .put(mark.getAttendence_id().getDate(), mark.getAttendant());
        }

        // Anyone currently on the roll appears even with no marks yet; anyone
        // who left mid-month keeps the marks they earned before they went.
        for (Student student : rollOf(classroom.getId())) {
            students.putIfAbsent(student.getId(), student);
        }

        List<Student> ordered = students.values().stream()
                .sorted((left, right) -> nullSafe(left.getStu_no()).compareTo(nullSafe(right.getStu_no())))
                .toList();

        // Weeks, in the order they occur, so the labels read Week 1, 2, 3...
        List<Integer> weeks = new ArrayList<>(new LinkedHashSet<>(
                days.stream().map(this::weekOf).toList()));

        List<ReportColumn> columns = new ArrayList<>();
        columns.add(ReportColumn.text("Adm. No."));
        columns.add(ReportColumn.wide("Name"));
        for (LocalDate day : days) {
            columns.add(new ReportColumn(String.valueOf(day.getDayOfMonth()), "center", 0.7f));
        }
        for (int index = 0; index < weeks.size(); index++) {
            columns.add(new ReportColumn("W" + (index + 1), "center", 1f));
        }
        columns.add(ReportColumn.number("Total"));

        List<List<String>> rows = new ArrayList<>();
        long[] dayTotals = new long[days.size()];
        long[] weekTotals = new long[weeks.size()];
        long grandTotal = 0;

        for (Student student : ordered) {
            Map<LocalDate, Boolean> own = marks.getOrDefault(student.getId(), Map.of());

            List<String> row = new ArrayList<>();
            row.add(nullSafe(student.getStu_no()));
            row.add(student.getFullname());

            long[] perWeek = new long[weeks.size()];
            long present = 0;

            for (int index = 0; index < days.size(); index++) {
                Boolean mark = own.get(days.get(index));
                if (mark == null) {
                    // Not marked is not the same as absent, and printing 0 here
                    // would quietly turn an unfinished register into an absence.
                    row.add("");
                    continue;
                }
                row.add(mark ? "1" : "0");
                if (mark) {
                    present++;
                    dayTotals[index]++;
                    perWeek[weeks.indexOf(weekOf(days.get(index)))]++;
                }
            }

            for (int index = 0; index < weeks.size(); index++) {
                row.add(String.valueOf(perWeek[index]));
                weekTotals[index] += perWeek[index];
            }

            row.add(String.valueOf(present));
            grandTotal += present;
            rows.add(row);
        }

        List<String> footer = new ArrayList<>();
        footer.add("Total");
        footer.add(rows.size() + " student(s)");
        for (long total : dayTotals) {
            footer.add(String.valueOf(total));
        }
        for (long total : weekTotals) {
            footer.add(String.valueOf(total));
        }
        footer.add(String.valueOf(grandTotal));

        String subtitle = days.isEmpty()
                ? "No register has been marked for this month yet."
                : days.size() + " day(s) of school · class teacher: " + teacherName(classroom);

        ReportSection section = new ReportSection(
                MONTH_TITLE.format(month), subtitle, columns, rows, days.isEmpty() ? null : footer);

        return document(ReportService.ATTENDANCE_REGISTER,
                "Attendance Register — " + classLabel(classroom) + " — " + MONTH_TITLE.format(month),
                "Daily attendance, one column per day school was conducted.",
                year, ReportLayout.LANDSCAPE, List.of(section));
    }

    // ---- Term summary -------------------------------------------------------

    /**
     * Days conducted, days attended and the percentage, per term and for the
     * year, for every student in one class.
     *
     * With no terms set up it falls back to a single "Full year" group rather
     * than refusing: the figures are still correct, just not broken down.
     */
    public ReportDocument termAttendance(ReportRequest request, AcademicYear year) {
        Classroom classroom = requireClassroom(request.requireClassroomId());

        List<Term> terms = termDao.listByAcademicYear(year.getId());
        List<Period> periods = terms.isEmpty()
                ? List.of(fullYear(year, classroom))
                : terms.stream().map(term -> new Period(term.getName(), term.getStart_date(), term.getEnd_date()))
                        .toList();

        List<Student> roll = rollOf(classroom.getId());

        List<ReportColumn> columns = new ArrayList<>();
        columns.add(ReportColumn.text("Adm. No."));
        columns.add(ReportColumn.wide("Name"));
        for (Period period : periods) {
            columns.add(new ReportColumn(period.name() + " — Days", "center", 1.2f));
            columns.add(new ReportColumn(period.name() + " — Present", "center", 1.2f));
            columns.add(new ReportColumn(period.name() + " — %", "center", 1.2f));
        }
        columns.add(new ReportColumn("Total — Days", "center", 1.2f));
        columns.add(new ReportColumn("Total — Present", "center", 1.2f));
        columns.add(new ReportColumn("Total — %", "center", 1.2f));

        // One pair of queries per period rather than per student.
        List<Long> conducted = new ArrayList<>();
        List<Map<Integer, Long>> presentByPeriod = new ArrayList<>();
        for (Period period : periods) {
            conducted.add(attendanceDao.countDays(classroom.getId(), period.from(), period.to()));
            presentByPeriod.add(CountByKey.toMap(
                    markDao.countPresentByStudent(classroom.getId(), period.from(), period.to())));
        }

        long totalConducted = conducted.stream().mapToLong(Long::longValue).sum();

        List<List<String>> rows = new ArrayList<>();
        long[] periodPresentTotals = new long[periods.size()];
        long grandPresent = 0;

        for (Student student : roll) {
            List<String> row = new ArrayList<>();
            row.add(nullSafe(student.getStu_no()));
            row.add(student.getFullname());

            long attended = 0;
            for (int index = 0; index < periods.size(); index++) {
                long days = conducted.get(index);
                long present = presentByPeriod.get(index).getOrDefault(student.getId(), 0L);

                row.add(String.valueOf(days));
                row.add(String.valueOf(present));
                row.add(percentage(present, days));

                attended += present;
                periodPresentTotals[index] += present;
            }

            row.add(String.valueOf(totalConducted));
            row.add(String.valueOf(attended));
            row.add(percentage(attended, totalConducted));

            grandPresent += attended;
            rows.add(row);
        }

        List<String> footer = new ArrayList<>();
        footer.add("Class");
        footer.add(rows.size() + " student(s)");
        for (int index = 0; index < periods.size(); index++) {
            // The class figure is attendances over the places available in the
            // period: days conducted times students on the roll.
            long available = conducted.get(index) * rows.size();
            footer.add(String.valueOf(conducted.get(index)));
            footer.add(String.valueOf(periodPresentTotals[index]));
            footer.add(percentage(periodPresentTotals[index], available));
        }
        footer.add(String.valueOf(totalConducted));
        footer.add(String.valueOf(grandPresent));
        footer.add(percentage(grandPresent, totalConducted * rows.size()));

        String subtitle = "Class teacher: " + teacherName(classroom)
                + (terms.isEmpty() ? " · no terms set up, showing the full year" : "");

        ReportSection section = new ReportSection(classLabel(classroom), subtitle, columns, rows, footer);

        return document(ReportService.TERM_ATTENDANCE,
                "Attendance Summary — " + classLabel(classroom),
                "Days conducted, days attended and the percentage, per term.",
                year, ReportLayout.LANDSCAPE, List.of(section));
    }

    // -------------------------------------------------------------------------

    /** A named date range: a term, or the whole year when none are defined. */
    private record Period(String name, LocalDate from, LocalDate to) {
    }

    /**
     * The year's span when no terms exist: its recorded dates if it has them,
     * otherwise the range the class actually has registers for.
     */
    private Period fullYear(AcademicYear year, Classroom classroom) {
        LocalDate from = year.getStart_date();
        LocalDate to = year.getEnd_date();

        if (from == null || to == null) {
            // Bounded by dates a DATE column can actually hold; LocalDate.MIN
            // is year -999999999 and neither MySQL nor H2 will accept it.
            List<LocalDate> dates = attendanceDao
                    .listByClassroomBetween(classroom.getId(), EARLIEST, LATEST).stream()
                    .map(register -> register.getDate())
                    .toList();
            from = dates.isEmpty() ? LocalDate.now() : dates.get(0);
            to = dates.isEmpty() ? LocalDate.now() : dates.get(dates.size() - 1);
        }

        return new Period("Full year", from, to);
    }

    private int weekOf(LocalDate date) {
        return date.get(WeekFields.of(Locale.UK).weekOfWeekBasedYear());
    }

    /** Actively enrolled, not deleted, in admission-number order. */
    private List<Student> rollOf(Integer classroomId) {
        return registrationDao.listByClassroom(classroomId).stream()
                .filter(this::isLive)
                .map(StudentRegistration::getStudent_id)
                .sorted((left, right) -> nullSafe(left.getStu_no()).compareTo(nullSafe(right.getStu_no())))
                .toList();
    }

    private boolean isLive(StudentRegistration registration) {
        var status = registration.getRegistration_status_id();
        if (status != null && !"active".equalsIgnoreCase(status.getName())) {
            return false;
        }
        var studentStatus = registration.getStudent_id().getStudent_status_id();
        return studentStatus == null || !"deleted".equalsIgnoreCase(studentStatus.getName());
    }

    private String teacherName(Classroom classroom) {
        return classroom.getEmployee_id() == null ? "Not assigned" : classroom.getEmployee_id().getFullname();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private Classroom requireClassroom(Integer id) {
        return classroomDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Class " + id + " does not exist."));
    }
}
