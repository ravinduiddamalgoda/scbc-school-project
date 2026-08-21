package com.scbck.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scbck.dto.SbaSheet;
import com.scbck.exception.ApiException;
import com.scbck.model.Classroom;
import com.scbck.model.SbaCandidate;
import com.scbck.model.SbaMark;
import com.scbck.model.SchoolProfile;
import com.scbck.model.Student;
import com.scbck.model.StudentRegistration;
import com.scbck.model.SubjectDetail;
import com.scbck.model.User;
import com.scbck.repository.ClassroomDao;
import com.scbck.repository.SbaCandidateDao;
import com.scbck.repository.SbaMarkDao;
import com.scbck.repository.StudentRegistrationDao;
import com.scbck.repository.SubjectDetailDao;
import com.scbck.repository.UserDao;

/**
 * School Based Assessment: who the candidates are, what they scored, and the
 * merged sheet the Department is sent.
 *
 * The candidates are not a list somebody types. They are the students enrolled
 * in the examination grade in the examination year - grade 13 for this year's
 * A/L, grade 11 for this year's O/L - which is exactly what the school asked
 * for: "it must be loading all the students who sit for the examination in the
 * present year". Deriving them means a student who transfers in appears without
 * anyone remembering to add them, and one who leaves stops appearing.
 */
@Service
public class SbaService {

    private final SbaMarkDao markDao;
    private final SbaCandidateDao candidateDao;
    private final SubjectDetailDao subjectDao;
    private final ClassroomDao classroomDao;
    private final StudentRegistrationDao registrationDao;
    private final UserDao userDao;
    private final PrivilegeService privilegeService;

    public SbaService(SbaMarkDao markDao, SbaCandidateDao candidateDao, SubjectDetailDao subjectDao,
            ClassroomDao classroomDao, StudentRegistrationDao registrationDao, UserDao userDao,
            PrivilegeService privilegeService) {
        this.markDao = markDao;
        this.candidateDao = candidateDao;
        this.subjectDao = subjectDao;
        this.classroomDao = classroomDao;
        this.registrationDao = registrationDao;
        this.userDao = userDao;
        this.privilegeService = privilegeService;
    }

    /**
     * The merged sheet for one subject.
     *
     * @param exam     {@code AL} or {@code OL}
     * @param examYear the year of the examination; defaults to this year
     */
    @Transactional(readOnly = true)
    public SbaSheet sheet(String exam, Integer examYear, Integer subjectId, String medium) {
        String kind = normaliseExam(exam);
        int year = examYear == null ? LocalDate.now().getYear() : examYear;

        SubjectDetail subject = subjectDao.findById(subjectId)
                .orElseThrow(() -> ApiException.notFound("Subject " + subjectId + " does not exist."));

        List<SbaSheet.Column> columns = columnsFor(kind);
        List<Student> candidates = candidatesFor(kind, year);

        // Indexed by (student, grade, term) so building a row is a lookup
        // rather than a scan of every mark for every column.
        Map<String, Integer> marks = new LinkedHashMap<>();
        for (SbaMark mark : markDao.listForSheet(kind, year, subjectId)) {
            marks.put(markKey(mark.getStudent().getId(), mark.getGradeNumber(), mark.getTermNumber()),
                    mark.getMarks());
        }

        Map<Integer, SbaCandidate> extras = new LinkedHashMap<>();
        for (SbaCandidate candidate : candidateDao.listForSheet(kind, year, subjectId)) {
            extras.put(candidate.getStudent().getId(), candidate);
        }

        List<SbaSheet.Row> rows = new ArrayList<>();
        int index = 1;
        for (Student student : candidates) {
            SbaCandidate extra = extras.get(student.getId());

            List<Integer> line = new ArrayList<>();
            int total = 0;
            for (SbaSheet.Column column : columns) {
                Integer mark = marks.get(markKey(student.getId(), column.grade(), column.term()));
                line.add(mark);
                if (mark != null) {
                    total += mark;
                }
            }

            Integer project = extra == null ? null : extra.getProjectMarks();
            if (project != null) {
                total += project;
            }

            rows.add(new SbaSheet.Row(
                    index++,
                    student.getId(),
                    student.getStu_no(),
                    nameWithInitials(student),
                    extra == null ? null : extra.getGroupName(),
                    project,
                    line,
                    total));
        }

        return new SbaSheet(
                kind,
                SbaMark.AL.equals(kind) ? "G.C.E. A/L" : "G.C.E. O/L",
                year,
                subject.getId(),
                subject.getName(),
                subject.getExamCode(),
                medium == null || medium.isBlank() ? "Sinhala" : medium,
                SchoolProfile.NAME,
                SchoolProfile.SCHOOL_ID,
                SchoolProfile.CENSUS_NO,
                SchoolProfile.ZONE,
                columns,
                rows);
    }

    /**
     * Saves one entry grid: one subject, one grade, one term.
     *
     * Only the cells actually sent are touched. Marks for the other four
     * columns belong to other grades and other terms, often entered a year
     * earlier by a different teacher, and a whole-sheet save would let this
     * term's entry silently blank them.
     */
    @Transactional
    public SbaSheet saveEntries(String exam, Integer examYear, Integer subjectId, Integer gradeNumber,
            Integer termNumber, List<Entry> entries, String medium) {

        privilegeService.requireUpdate(PrivilegeService.MODULE_SBA);

        String kind = normaliseExam(exam);
        int year = examYear == null ? LocalDate.now().getYear() : examYear;

        SubjectDetail subject = subjectDao.findById(subjectId)
                .orElseThrow(() -> ApiException.notFound("Subject " + subjectId + " does not exist."));

        assertGradeAndTerm(kind, gradeNumber, termNumber);

        Map<Integer, Student> candidates = new LinkedHashMap<>();
        for (Student student : candidatesFor(kind, year)) {
            candidates.put(student.getId(), student);
        }

        User currentUser = userDao.getByUsername(privilegeService.currentUsername());
        Integer userId = currentUser == null ? null : currentUser.getId();

        for (Entry entry : entries == null ? List.<Entry>of() : entries) {
            Student student = candidates.get(entry.studentId());
            if (student == null) {
                throw ApiException.badRequest(
                        "Student " + entry.studentId() + " is not a candidate for this examination.");
            }

            saveMark(kind, year, subject, gradeNumber, termNumber, student, entry.marks(), userId);
            saveCandidateExtras(kind, year, subject, student, entry);
        }

        return sheet(kind, year, subjectId, medium);
    }

    /**
     * One row of the entry grid, as the browser sends it.
     *
     * All three values are always present for a row that was touched - see
     * {@link #saveCandidateExtras} for why the payload is treated as complete
     * rather than as a patch.
     */
    public record Entry(Integer studentId, Integer marks, String groupName, Integer projectMarks) {
    }

    // -------------------------------------------------------------------------

    private void saveMark(String exam, int year, SubjectDetail subject, Integer grade, Integer term,
            Student student, Integer marks, Integer userId) {

        SbaMark existing = markDao
                .find(exam, year, subject.getId(), grade, term, student.getId())
                .orElse(null);

        if (marks == null) {
            // Clearing a cell removes the row rather than storing a null: "not
            // assessed" is the absence of a mark, and keeping an empty row
            // would make the sheet look assessed on the day it was opened.
            if (existing != null) {
                markDao.delete(existing);
            }
            return;
        }

        SbaMark row = existing == null ? new SbaMark() : existing;
        row.setStudent(student);
        row.setSubject(subject);
        row.setExam(exam);
        row.setExamYear(year);
        row.setGradeNumber(grade);
        row.setTermNumber(term);
        row.setMarks(marks);
        row.setUpdated_user_id(userId);
        if (row.getAdded_datetime() == null) {
            row.setAdded_datetime(LocalDateTime.now());
        }
        row.setUpdated_datetime(LocalDateTime.now());

        markDao.save(row);
    }

    /**
     * Stores the group and project columns, which belong to the whole
     * assessment rather than to the term being entered.
     *
     * The entry grid sends all three of a touched row's values together, not
     * only the one that changed, so this takes the payload as the whole truth
     * for that candidate. That is deliberate: JSON cannot distinguish a field
     * deliberately cleared from one the browser left out, and guessing wrong
     * either loses a project mark or refuses to let one be erased.
     *
     * A row with nothing in either column and no record already stored writes
     * nothing, so opening the grid and saving one term mark does not create an
     * empty candidate row for everybody.
     */
    private void saveCandidateExtras(String exam, int year, SubjectDetail subject, Student student,
            Entry entry) {

        SbaCandidate existing = candidateDao
                .find(exam, year, subject.getId(), student.getId())
                .orElse(null);

        boolean empty = (entry.groupName() == null || entry.groupName().isBlank())
                && entry.projectMarks() == null;

        if (existing == null && empty) {
            return;
        }

        SbaCandidate candidate = existing == null ? new SbaCandidate() : existing;
        candidate.setStudent(student);
        candidate.setSubject(subject);
        candidate.setExam(exam);
        candidate.setExamYear(year);
        candidate.setGroupName(entry.groupName() == null || entry.groupName().isBlank()
                ? null
                : entry.groupName().trim());
        candidate.setProjectMarks(entry.projectMarks());

        candidateDao.save(candidate);
    }

    /**
     * The students sitting the examination in the given year.
     *
     * Grade 13 sits the A/L and grade 11 sits the O/L, so the candidates are
     * that grade's enrolments in that year - derived, never typed. A student
     * who has left is excluded by the same filter every head-count report uses,
     * so a candidate list and a class list can never disagree.
     */
    @Transactional(readOnly = true)
    public List<Student> candidatesFor(String exam, int examYear) {
        int sittingGrade = SbaMark.AL.equals(exam) ? 13 : 11;

        List<Student> students = new ArrayList<>();
        for (Classroom classroom : classroomDao.findAll()) {
            if (classroom.getGrade_id() == null || classroom.getAcademic_year_id() == null) {
                continue;
            }
            if (!Objects.equals(numberOf(classroom.getGrade_id().getName()), sittingGrade)) {
                continue;
            }
            if (!Objects.equals(yearNumberOf(classroom.getAcademic_year_id().getName()), examYear)) {
                continue;
            }

            registrationDao.listByClassroom(classroom.getId()).stream()
                    .filter(SbaService::isLive)
                    .map(StudentRegistration::getStudent_id)
                    .forEach(students::add);
        }

        students.sort(Comparator.comparing(
                student -> student.getStu_no() == null ? "" : student.getStu_no()));
        return students;
    }

    /** Same filter the count reports use, so a roll and a head count agree. */
    private static boolean isLive(StudentRegistration registration) {
        var status = registration.getRegistration_status_id();
        if (status != null && !"active".equalsIgnoreCase(status.getName())) {
            return false;
        }
        var studentStatus = registration.getStudent_id().getStudent_status_id();
        return studentStatus == null || !"deleted".equalsIgnoreCase(studentStatus.getName());
    }

    /**
     * The five assessment columns, senior grade first.
     *
     * The Department prints the most recent term leftmost within each grade,
     * which is why the terms run 2, 1 and 3, 2, 1 rather than upwards.
     */
    private List<SbaSheet.Column> columnsFor(String exam) {
        List<SbaSheet.Column> columns = new ArrayList<>();

        List<Integer> grades = new ArrayList<>(SbaMark.gradesFor(exam));
        java.util.Collections.reverse(grades);

        for (int grade : grades) {
            List<Integer> terms = new ArrayList<>(SbaMark.termsFor(exam, grade));
            java.util.Collections.reverse(terms);
            for (int term : terms) {
                columns.add(new SbaSheet.Column(grade, term, "Grade " + grade, ordinal(term) + " Term"));
            }
        }
        return columns;
    }

    private void assertGradeAndTerm(String exam, Integer grade, Integer term) {
        if (grade == null || !SbaMark.gradesFor(exam).contains(grade)) {
            throw ApiException.badRequest("The " + exam + " assessment covers grades "
                    + SbaMark.gradesFor(exam) + ", not grade " + grade + ".");
        }
        if (term == null || !SbaMark.termsFor(exam, grade).contains(term)) {
            throw ApiException.badRequest("Grade " + grade + " contributes terms "
                    + SbaMark.termsFor(exam, grade) + " to the " + exam + " assessment.");
        }
    }

    public static String normaliseExam(String exam) {
        String value = exam == null ? "" : exam.trim().toUpperCase();
        if (!SbaMark.EXAMS.contains(value)) {
            throw ApiException.badRequest("An assessment is either AL or OL, not '" + exam + "'.");
        }
        return value;
    }

    /**
     * "Samith Bandara Kandegedara" as "S . B . Kandegedara".
     *
     * The Department's sheet is a name-with-initials column, and the school's
     * own sample spaces the initials out exactly like this. The record's
     * calling name is preferred when it already has that form.
     */
    private static String nameWithInitials(Student student) {
        String calling = student.getCallingname();
        if (calling != null && calling.contains(".")) {
            return calling;
        }

        String full = student.getFullname() == null ? "" : student.getFullname().trim();
        String[] parts = full.split("\\s+");
        if (parts.length <= 1) {
            return full;
        }

        StringBuilder built = new StringBuilder();
        for (int index = 0; index < parts.length - 1; index++) {
            if (parts[index].isEmpty()) {
                continue;
            }
            built.append(Character.toUpperCase(parts[index].charAt(0))).append(" . ");
        }
        return built.append(parts[parts.length - 1]).toString();
    }

    private static String markKey(Integer studentId, Integer grade, Integer term) {
        return studentId + ":" + grade + ":" + term;
    }

    private static String ordinal(int term) {
        return switch (term) {
            case 1 -> "1st";
            case 2 -> "2nd";
            default -> "3rd";
        };
    }

    /** "Grade 13" -> 13. */
    private static Integer numberOf(String name) {
        return digitsOf(name);
    }

    /** "2026" -> 2026. Also copes with a year named "2026 / 2027". */
    private static Integer yearNumberOf(String name) {
        if (name == null) {
            return null;
        }
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("\\d{4}").matcher(name);
        return matcher.find() ? Integer.valueOf(matcher.group()) : null;
    }

    private static Integer digitsOf(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D+", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(digits);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
