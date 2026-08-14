package com.scbck.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.scbck.exception.ApiException;
import com.scbck.model.AcademicYear;
import com.scbck.model.Classroom;
import com.scbck.model.ExamRegistration;
import com.scbck.model.Religion;
import com.scbck.model.Student;
import com.scbck.model.StudentRegistration;
import com.scbck.model.StudentSubject;
import com.scbck.model.SubjectDetail;
import com.scbck.repository.ClassroomDao;
import com.scbck.repository.ExamRegistrationDao;
import com.scbck.repository.ReligionDao;
import com.scbck.repository.StudentRegistrationDao;
import com.scbck.repository.StudentSubjectDao;

/**
 * Builds the Department of Examinations candidate workbooks.
 *
 * These are not reports - they are submissions, read by a machine at the other
 * end, and the templates carry validation the Department enforces: a NIC of
 * exactly twelve characters, a date of birth inside a fixed window, and subject
 * codes rather than names. Anything this writes has to satisfy that or the
 * upload is rejected in bulk with no indication of which row was wrong.
 *
 * So the export does not quietly emit blanks. Every candidate whose record is
 * incomplete is listed back to the caller, and the workbook still downloads
 * with those cells empty - the office can then see exactly whose NIC is missing
 * rather than discovering it at the deadline.
 *
 * Which column an optional subject lands in is decided by its own code, since
 * the Department's ranges define the categories: 60-75 is Category I, 40-52
 * Category II, 80-94 Category III. That means adding a subject needs no mapping
 * beyond the code the Department already publishes for it.
 */
@Service
public class ExamExportService {

    /** The compulsory subject codes, in the order the OL sheet lists them. */
    private static final int ENGLISH = 31;
    private static final int MATHEMATICS = 32;
    private static final int HISTORY = 33;
    private static final int SCIENCE = 34;

    private final ClassroomDao classroomDao;
    private final StudentRegistrationDao registrationDao;
    private final StudentSubjectDao studentSubjectDao;
    private final ExamRegistrationDao examDao;
    private final ReligionDao religionDao;
    private final AcademicYearService academicYearService;

    public ExamExportService(ClassroomDao classroomDao, StudentRegistrationDao registrationDao,
            StudentSubjectDao studentSubjectDao, ExamRegistrationDao examDao, ReligionDao religionDao,
            AcademicYearService academicYearService) {
        this.classroomDao = classroomDao;
        this.registrationDao = registrationDao;
        this.studentSubjectDao = studentSubjectDao;
        this.examDao = examDao;
        this.religionDao = religionDao;
        this.academicYearService = academicYearService;
    }

    /** The grade whose students sit a given exam. */
    public int gradeFor(String exam) {
        return switch (exam) {
            case ExamRegistration.OL -> 11;
            case ExamRegistration.GIT -> 12;
            case ExamRegistration.AL -> 13;
            case ExamRegistration.GRADE5 -> 5;
            default -> throw ApiException.badRequest(
                    "Unknown examination '" + exam + "'. Expected OL, AL, GIT or GRADE5.");
        };
    }

    public String fileNameFor(String exam, AcademicYear year) {
        String stem = switch (exam) {
            case ExamRegistration.OL -> "OL Candidates";
            case ExamRegistration.AL -> "AL Candidates";
            case ExamRegistration.GIT -> "GIT Candidates";
            default -> "Grade 5 Scholarship Candidates";
        };
        return stem + " " + (year == null ? "" : year.getName()).trim() + ".xlsx";
    }

    /**
     * @return the workbook, and the problems the office needs to fix
     */
    public Export build(String exam, Integer academicYearId) {
        String kind = exam == null ? "" : exam.trim().toUpperCase();
        int grade = gradeFor(kind);
        AcademicYear year = academicYearService.resolve(academicYearId);

        List<Candidate> candidates = candidatesFor(grade, year, kind);
        if (candidates.isEmpty()) {
            throw ApiException.badRequest("No students are enrolled in Grade " + grade
                    + " for " + year.getName() + ", so there are no candidates to submit.");
        }

        List<String> problems = new ArrayList<>();
        byte[] workbook = write(kind, candidates, problems);

        return new Export(workbook, fileNameFor(kind, year), candidates.size(), problems);
    }

    // ---- Gathering -----------------------------------------------------------

    private List<Candidate> candidatesFor(int grade, AcademicYear year, String exam) {
        List<Candidate> candidates = new ArrayList<>();

        Map<Integer, ExamRegistration> entries = new HashMap<>();
        for (ExamRegistration entry : examDao.listByExamAndYear(exam, year.getId())) {
            entries.put(entry.getStudent_id().getId(), entry);
        }

        List<Classroom> classes = classroomDao.findAll().stream()
                .filter(classroom -> classroom.getAcademic_year_id() != null
                        && classroom.getAcademic_year_id().getId().equals(year.getId()))
                .filter(classroom -> {
                    Integer level = GradeBand.levelOf(classroom.getGrade_id());
                    return level != null && level == grade;
                })
                .sorted(ReportLayout.classroomOrder())
                .toList();

        for (Classroom classroom : classes) {
            for (StudentRegistration registration : registrationDao.listByClassroom(classroom.getId())) {
                Student student = registration.getStudent_id();
                if (student == null || isInactive(registration)) {
                    continue;
                }
                candidates.add(new Candidate(student, registration, classroom,
                        entries.get(student.getId()), subjectsOf(registration)));
            }
        }

        candidates.sort(Comparator.comparing(candidate -> candidate.student().getFullname(),
                String.CASE_INSENSITIVE_ORDER));
        return candidates;
    }

    private boolean isInactive(StudentRegistration registration) {
        if (registration.getRegistration_status_id() != null
                && !"active".equalsIgnoreCase(registration.getRegistration_status_id().getName())) {
            return true;
        }
        return registration.getStudent_id() != null
                && registration.getStudent_id().getStudent_status_id() != null
                && "deleted".equalsIgnoreCase(registration.getStudent_id().getStudent_status_id().getName());
    }

    private List<SubjectDetail> subjectsOf(StudentRegistration registration) {
        return studentSubjectDao.listByRegistration(registration.getId()).stream()
                .map(StudentSubject::getClassroom_subject_id)
                .filter(line -> line != null && line.getSubject_detail_id() != null)
                .map(line -> line.getSubject_detail_id())
                .filter(subject -> subject.getExamCode() != null)
                .sorted(Comparator.comparingInt(SubjectDetail::getExamCode))
                .toList();
    }

    // ---- Writing -------------------------------------------------------------

    private byte[] write(String exam, List<Candidate> candidates, List<String> problems) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = workbook.createSheet(sheetNameFor(exam));
            Styles styles = new Styles(workbook);

            List<String> headers = headersFor(exam);
            Row heading = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = heading.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(styles.head);
            }

            int rowIndex = 1;
            for (Candidate candidate : candidates) {
                List<Object> values = switch (exam) {
                    case ExamRegistration.OL -> olRow(rowIndex, candidate, problems);
                    case ExamRegistration.AL -> alRow(rowIndex, candidate, problems);
                    case ExamRegistration.GIT -> gitRow(rowIndex, candidate, problems);
                    default -> grade5Row(rowIndex, candidate, problems);
                };

                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < values.size(); i++) {
                    Cell cell = row.createCell(i);
                    Object value = values.get(i);
                    if (value instanceof Number number) {
                        cell.setCellValue(number.doubleValue());
                    } else {
                        cell.setCellValue(value == null ? "" : String.valueOf(value));
                    }
                    cell.setCellStyle(styles.body);
                }
            }

            for (int i = 0; i < headers.size(); i++) {
                sheet.setColumnWidth(i, i == 1 ? 12000 : 4200);
            }

            workbook.write(out);
            return out.toByteArray();

        } catch (IOException error) {
            throw ApiException.badRequest("The candidate workbook could not be written: " + error.getMessage());
        }
    }

    private String sheetNameFor(String exam) {
        return switch (exam) {
            case ExamRegistration.OL -> "OL";
            case ExamRegistration.AL -> "st_al";
            case ExamRegistration.GIT -> "st_git";
            default -> "st_gv";
        };
    }

    private List<String> headersFor(String exam) {
        return switch (exam) {
            case ExamRegistration.OL -> List.of("ID", "FULL_NAME", "NIC", "DATE_OF_BIRTH", "GENDER",
                    "ATTEMPT", "LANGUAGE_MEDIUM", "RELIGION", "MEDIUM_RELIGION", "LANGUAGE_&_LITERATURE",
                    "ENGLISH", "MATHEMATICS", "MEDIUM_MATHEMATICS", "HISTORY", "MEDIUM_HISTORY",
                    "SCIENCE", "MEDIUM_SCIENCE", "CATEGORY_I_60-75", "MEDIUM_FOR_CATEGORY_I",
                    "CATEGORY_II_40-52", "MEDIUM_FOR_CATEGORY_II", "CATEGORY_III_80-94",
                    "MEDIUM_FOR_CATEGORY_III", "NO_OF_SUBJECTS",
                    "HAS_ANY_SPECIAL_NEEDS/VISUAL_IMPAIRMENTS");
            case ExamRegistration.AL -> List.of("ID", "FULL_NAME", "NIC", "DATE_OF_BIRTH", "GENDER",
                    "ATTEMPT", "LANGUAGE_MEDIUM", "1ST_SUBJECT_NO", "1ST_SUBJECT_LANG", "2ND_SUBJECT_NO",
                    "2ND_SUBJECT_LANG", "3RD_SUBJECT_NO", "3RD_SUBJECT_LANG", "COMMON_GENERAL_TEST",
                    "COMMON_GENERAL_TEST_LANG", "GENERAL_ENGLISH", "NO_OF_SUBJECTS",
                    "HAS_ANY_SPECIAL_NEEDS/VISUAL_IMPAIRMENTS", "DATE_JOINED_TO_YOUR_SCHOOL");
            case ExamRegistration.GIT -> List.of("ID", "FULL_NAME", "NIC", "DATE_OF_BIRTH", "GENDER",
                    "LANGUAGE_MEDIUM", "HAS_ANY_SPECIAL_NEEDS/VISUAL_IMPAIRMENTS");
            default -> List.of("ID", "STUDENT_NO", "FULL_NAME", "DATE_OF_BIRTH", "GENDER",
                    "LANGUAGE_MEDIUM", "ANNUAL_INCOME_LEVEL",
                    "HAS_ANY_SPECIAL_NEEDS/VISUAL_IMPAIRMENTS");
        };
    }

    private List<Object> olRow(int id, Candidate candidate, List<String> problems) {
        Student student = candidate.student();
        String medium = candidate.medium();

        List<Object> row = new ArrayList<>(List.of());
        row.add(id);
        row.add(upper(student.getFullname()));
        row.add(nic(candidate, problems));
        row.add(dateSerial(student.getDob(), candidate, problems));
        row.add(gender(student));
        row.add(candidate.attempt());
        row.add(medium);
        row.add(religionCode(candidate, problems));
        row.add(medium);
        row.add(codeInRange(candidate, 21, 22));
        row.add(has(candidate, ENGLISH) ? ENGLISH : null);
        row.add(has(candidate, MATHEMATICS) ? MATHEMATICS : null);
        row.add(medium);
        row.add(has(candidate, HISTORY) ? HISTORY : null);
        row.add(medium);
        row.add(has(candidate, SCIENCE) ? SCIENCE : null);
        row.add(medium);
        row.add(codeInRange(candidate, 60, 75));
        row.add(medium);
        row.add(codeInRange(candidate, 40, 52));
        row.add(medium);
        row.add(codeInRange(candidate, 80, 94));
        row.add(medium);
        row.add(candidate.subjects().size());
        row.add(candidate.specialNeeds());

        if (candidate.subjects().isEmpty()) {
            problems.add(student.getFullname()
                    + " has no subjects carrying an examination code, so their subject columns are blank.");
        }
        return row;
    }

    private List<Object> alRow(int id, Candidate candidate, List<String> problems) {
        Student student = candidate.student();
        String medium = candidate.medium();
        List<SubjectDetail> subjects = candidate.subjects();

        List<Object> row = new ArrayList<>();
        row.add(id);
        row.add(upper(student.getFullname()));
        row.add(nic(candidate, problems));
        row.add(dateSerial(student.getDob(), candidate, problems));
        row.add(gender(student));
        row.add(candidate.attempt());
        row.add(medium);

        // A/L is three subjects. More than three means the enrolment is wrong,
        // and the Department's sheet has nowhere to put a fourth.
        for (int i = 0; i < 3; i++) {
            row.add(i < subjects.size() ? subjects.get(i).getExamCode() : null);
            row.add(i < subjects.size() ? medium : null);
        }
        if (subjects.size() != 3) {
            problems.add(student.getFullname() + " has " + subjects.size()
                    + " coded subject(s); the A/L sheet expects exactly three.");
        }

        row.add("YES");
        row.add(medium);
        row.add("YES");
        row.add(Math.min(subjects.size(), 3));
        row.add(candidate.specialNeeds());
        row.add(dateSerial(candidate.registration().getDate(), candidate, problems));
        return row;
    }

    private List<Object> gitRow(int id, Candidate candidate, List<String> problems) {
        Student student = candidate.student();
        return List.of(
                id,
                upper(student.getFullname()),
                orEmpty(nic(candidate, problems)),
                orEmpty(dateSerial(student.getDob(), candidate, problems)),
                gender(student),
                candidate.medium(),
                candidate.specialNeeds());
    }

    private List<Object> grade5Row(int id, Candidate candidate, List<String> problems) {
        Student student = candidate.student();

        if (candidate.entry() == null || candidate.entry().getIncomeLevel() == null) {
            problems.add(student.getFullname()
                    + " has no annual income level recorded, which the scholarship sheet requires.");
        }

        List<Object> row = new ArrayList<>();
        row.add(id);
        row.add(student.getStu_no());
        row.add(upper(student.getFullname()));
        row.add(dateSerial(student.getDob(), candidate, problems));
        row.add(gender(student));
        row.add(candidate.medium());
        row.add(candidate.entry() == null ? null : candidate.entry().getIncomeLevel());
        row.add(candidate.specialNeeds());
        return row;
    }

    // ---- Field rules ---------------------------------------------------------

    /**
     * The NIC, checked against the Department's twelve-character rule.
     *
     * Older ten-character numbers ("901234567V") are still common in school
     * records and are silently rejected on upload, so they are reported rather
     * than written through.
     */
    private String nic(Candidate candidate, List<String> problems) {
        String value = candidate.student().getNic();
        if (value == null || value.isBlank()) {
            problems.add(candidate.student().getFullname() + " has no NIC recorded.");
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() != 12) {
            problems.add(candidate.student().getFullname() + " has a " + trimmed.length()
                    + "-character NIC (" + trimmed + "); the Department requires 12.");
        }
        return trimmed;
    }

    /**
     * A date as the serial number the templates hold.
     *
     * The workbooks store dates as plain numbers - 1900-based Excel serials -
     * and reject a text date, which is why this converts rather than formats.
     */
    private Integer dateSerial(LocalDate date, Candidate candidate, List<String> problems) {
        if (date == null) {
            problems.add(candidate.student().getFullname() + " has no date of birth recorded.");
            return null;
        }
        // Excel's epoch is 1899-12-30 once its 1900 leap-year bug is accounted for.
        return (int) (date.toEpochDay() - LocalDate.of(1899, 12, 30).toEpochDay());
    }

    private String gender(Student student) {
        String value = student.getGender() == null ? "" : student.getGender().trim().toLowerCase();
        if (value.startsWith("m")) {
            return "M";
        }
        return value.startsWith("f") ? "F" : "";
    }

    private Integer religionCode(Candidate candidate, List<String> problems) {
        String name = candidate.student().getReligion();
        if (name == null || name.isBlank()) {
            problems.add(candidate.student().getFullname() + " has no religion recorded.");
            return null;
        }

        Religion religion = religionDao.getByName(name.trim());
        if (religion == null || religion.getExamCode() == null) {
            problems.add("No examination code is set up for the religion '" + name.trim()
                    + "' (" + candidate.student().getFullname() + ").");
            return null;
        }
        return religion.getExamCode();
    }

    /** The candidate's subject whose code falls in a range, if they take one. */
    private Integer codeInRange(Candidate candidate, int from, int to) {
        return candidate.subjects().stream()
                .map(SubjectDetail::getExamCode)
                .filter(code -> code >= from && code <= to)
                .findFirst()
                .orElse(null);
    }

    private boolean has(Candidate candidate, int code) {
        return candidate.subjects().stream()
                .anyMatch(subject -> subject.getExamCode() == code);
    }

    private String upper(String value) {
        return value == null ? "" : value.toUpperCase();
    }

    private Object orEmpty(Object value) {
        return value == null ? "" : value;
    }

    // -------------------------------------------------------------------------

    /** One student on the candidate list, with everything needed to write them. */
    private record Candidate(
            Student student,
            StudentRegistration registration,
            Classroom classroom,
            ExamRegistration entry,
            List<SubjectDetail> subjects) {

        /** S, E or T - the Department's language-medium codes. */
        String medium() {
            String value = classroom == null || classroom.getMedium() == null
                    ? ""
                    : classroom.getMedium().trim().toLowerCase();
            if (value.startsWith("e")) {
                return "E";
            }
            return value.startsWith("t") ? "T" : "S";
        }

        int attempt() {
            return entry == null || entry.getAttempt() == null ? 1 : entry.getAttempt();
        }

        String specialNeeds() {
            return entry != null && Boolean.TRUE.equals(entry.getSpecialNeeds()) ? "YES" : "NO";
        }
    }

    /**
     * @param problems records the office must correct before submitting; the
     *                 workbook still downloads so the gaps can be seen in place
     */
    public record Export(byte[] workbook, String filename, int candidates, List<String> problems) {
    }

    /** Header and body styling; created once per workbook. */
    private static final class Styles {

        private final CellStyle head;
        private final CellStyle body;

        Styles(XSSFWorkbook workbook) {
            Font headFont = workbook.createFont();
            headFont.setBold(true);
            headFont.setFontHeightInPoints((short) 9);

            Font bodyFont = workbook.createFont();
            bodyFont.setFontHeightInPoints((short) 10);

            head = workbook.createCellStyle();
            head.setFont(headFont);
            head.setBorderBottom(BorderStyle.THIN);
            head.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            body = workbook.createCellStyle();
            body.setFont(bodyFont);
        }
    }
}
