package com.scbck.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.scbck.dto.ReportDocument;
import com.scbck.dto.ReportRequest;
import com.scbck.dto.ReportSummary;
import com.scbck.exception.ApiException;
import com.scbck.model.AcademicYear;

/**
 * The report catalogue, and the one place a report key turns into a report.
 *
 * The builders live in {@link ClassReports}, {@link AttendanceReports} and
 * {@link FeeReports}, grouped by what they describe rather than by who asked
 * for them. This class knows only the names, the parameters each one needs,
 * and where to send the request - which is what keeps a ninth report from
 * touching a single line of the endpoint, the PDF writer or the screen.
 */
@Service
public class ReportService {

    public static final String CLASS_TEACHERS = "class-teachers";
    public static final String STUDENT_COUNTS = "student-counts";
    public static final String SUBJECT_TEACHERS = "subject-teachers";
    public static final String SUBJECT_STUDENT_COUNTS = "subject-student-counts";
    public static final String MEDIUM_COUNTS = "medium-counts";
    public static final String GRADE_HEADS = "grade-heads";
    public static final String ATTENDANCE_REGISTER = "attendance-register";
    public static final String TERM_ATTENDANCE = "term-attendance";
    public static final String FEE_DETAILS = "fee-details";

    /**
     * The order reports appear in the client's menu, and the inputs each one
     * needs. The client renders its parameter controls from this list, so a
     * report requiring a class gets a class picker without anything on the
     * client being told about it.
     */
    private static final List<ReportSummary> CATALOGUE = List.of(
            ReportSummary.yearly(CLASS_TEACHERS, "Class Teachers",
                    "Every class in the year and the teacher responsible for it."),
            ReportSummary.yearly(STUDENT_COUNTS, "Student Count of Classes",
                    "How many students are on the roll of each class."),
            ReportSummary.yearly(SUBJECT_TEACHERS, "Subject Wise Teachers",
                    "How many teachers take each subject, grade by grade."),
            ReportSummary.yearly(SUBJECT_STUDENT_COUNTS, "Subject wise Student Count of Classes",
                    "How many students in each class take each subject."),
            ReportSummary.yearly(MEDIUM_COUNTS, "Medium wise Student Count of Classes",
                    "How many students sit in each medium of instruction."),
            ReportSummary.yearly(GRADE_HEADS, "Grade Heads",
                    "The teacher responsible for each grade."),
            new ReportSummary(ATTENDANCE_REGISTER, "Attendance Register",
                    "One class's daily register for a month, with weekly totals.",
                    List.of(ReportRequest.ACADEMIC_YEAR, ReportRequest.CLASSROOM, ReportRequest.MONTH)),
            new ReportSummary(TERM_ATTENDANCE, "Week Attendance Summary",
                    "Days conducted, days attended and the percentage, per term.",
                    List.of(ReportRequest.ACADEMIC_YEAR, ReportRequest.CLASSROOM)),
            new ReportSummary(FEE_DETAILS, "Fees Details",
                    "Every fee payment recorded against one student.",
                    List.of(ReportRequest.ACADEMIC_YEAR, ReportRequest.STUDENT)));

    private final ClassReports classReports;
    private final AttendanceReports attendanceReports;
    private final FeeReports feeReports;
    private final AcademicYearService academicYearService;

    public ReportService(ClassReports classReports, AttendanceReports attendanceReports,
            FeeReports feeReports, AcademicYearService academicYearService) {
        this.classReports = classReports;
        this.attendanceReports = attendanceReports;
        this.feeReports = feeReports;
        this.academicYearService = academicYearService;
    }

    public List<ReportSummary> catalogue() {
        return CATALOGUE;
    }

    public ReportDocument build(String key, ReportRequest request) {
        AcademicYear year = academicYearService.resolve(request.academicYearId());

        return switch (key) {
            case CLASS_TEACHERS -> classReports.classTeachers(year);
            case STUDENT_COUNTS -> classReports.studentCounts(year);
            case SUBJECT_TEACHERS -> classReports.subjectTeachers(year);
            case SUBJECT_STUDENT_COUNTS -> classReports.subjectStudentCounts(year);
            case MEDIUM_COUNTS -> classReports.mediumCounts(year);
            case GRADE_HEADS -> classReports.gradeHeads(year);
            case ATTENDANCE_REGISTER -> attendanceReports.register(request, year);
            case TERM_ATTENDANCE -> attendanceReports.termAttendance(request, year);
            case FEE_DETAILS -> feeReports.feeDetails(request, year);
            default -> throw ApiException.notFound("There is no report called '" + key + "'.");
        };
    }

    /**
     * A filename stem for the download, e.g. "Class-Teachers-2026".
     *
     * The title already carries the class or student for the reports scoped to
     * one, so a folder of saved PDFs stays self-describing.
     */
    public String fileNameFor(ReportDocument document) {
        String stem = document.title().replaceAll("[^A-Za-z0-9]+", "-").replaceAll("(^-|-$)", "");
        String year = document.academicYear() == null ? "" : "-" + document.academicYear().replaceAll("\\s+", "");
        return stem + year + ".pdf";
    }
}
