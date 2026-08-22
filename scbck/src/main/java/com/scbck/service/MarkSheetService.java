package com.scbck.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scbck.dto.MarkSheet;
import com.scbck.exception.ApiException;
import com.scbck.model.Classroom;
import com.scbck.model.ClassroomSubject;
import com.scbck.model.Employee;
import com.scbck.model.StudentMark;
import com.scbck.model.StudentRegistration;
import com.scbck.model.StudentSubject;
import com.scbck.model.GradeSubject;
import com.scbck.model.SubjectDetail;
import com.scbck.model.Term;
import com.scbck.repository.ClassroomDao;
import com.scbck.repository.ClassroomSubjectDao;
import com.scbck.repository.GradeSubjectDao;
import com.scbck.repository.StudentMarkDao;
import com.scbck.repository.StudentRegistrationDao;
import com.scbck.repository.StudentSubjectDao;
import com.scbck.repository.TermDao;

/**
 * Builds a class's mark sheet for a term: the roster, the marks, and everything
 * the workbook derived with formulas.
 *
 * The calculations follow the school's own sheet, with two deliberate
 * departures, both places where the formula was wrong rather than merely
 * different:
 *
 * <ul>
 * <li>The average divides by the number of results actually recorded, not by
 * the literal 9 the workbook hard-codes. Nine was right for the class it was
 * written for; a student carrying an extra optional subject, or a grade with a
 * different basket structure, got an average over the wrong base and no
 * indication of it.</li>
 * <li>A subject nobody has entered a mark for yet is blank, not zero, and does
 * not count towards the divisor. The workbook's SUM treats a missing mark and a
 * zero identically, so a term half-entered showed averages that looked final
 * and ranked students on them.</li>
 * </ul>
 *
 * An absence is a recorded result: it contributes nothing to the total but does
 * count in the divisor, which is what the workbook does - "ab" is text, so SUM
 * skips it while the divisor stays put. A student with nothing recorded at all
 * has no average and therefore no rank, rather than placing last on zero.
 */
@Service
public class MarkSheetService {

    /** Rows at or above this average are emphasised on every rendering. */
    public static final double HIGHLIGHT_AVERAGE = 80.0;

    private final ClassroomDao classroomDao;
    private final ClassroomSubjectDao classroomSubjectDao;
    private final StudentRegistrationDao registrationDao;
    private final StudentSubjectDao studentSubjectDao;
    private final StudentMarkDao markDao;
    private final TermDao termDao;
    private final GradeSubjectDao gradeSubjectDao;

    public MarkSheetService(ClassroomDao classroomDao, ClassroomSubjectDao classroomSubjectDao,
            StudentRegistrationDao registrationDao, StudentSubjectDao studentSubjectDao,
            StudentMarkDao markDao, TermDao termDao, GradeSubjectDao gradeSubjectDao) {
        this.classroomDao = classroomDao;
        this.classroomSubjectDao = classroomSubjectDao;
        this.registrationDao = registrationDao;
        this.studentSubjectDao = studentSubjectDao;
        this.markDao = markDao;
        this.termDao = termDao;
        this.gradeSubjectDao = gradeSubjectDao;
    }

    @Transactional(readOnly = true)
    public MarkSheet build(Integer classroomId, Integer termId) {

        Classroom classroom = classroomDao.findById(classroomId)
                .orElseThrow(() -> ApiException.notFound("Class " + classroomId + " does not exist."));
        Term term = requireTermOf(classroom, termId);

        Map<Integer, GradeSubject> curriculum = curriculumFor(classroom);
        List<ClassroomSubject> timetable = orderedTimetable(classroom, classroomId);
        if (timetable.isEmpty()) {
            throw ApiException.badRequest(ReportLayout.classLabel(classroom)
                    + " has no subjects on its timetable, so it has no mark sheet. Add subjects to the class first.");
        }

        List<StudentRegistration> roster = activeRoster(classroomId);

        // Enrolment lines and marks for the whole class in two queries, then
        // indexed - a per-student lookup would cost one round trip per row.
        Map<Integer, Map<Integer, StudentSubject>> enrolments = enrolmentsByRegistration(roster);
        Map<Integer, StudentMark> marks = marksByStudentSubject(classroomId, term.getId());

        List<MarkSheet.Subject> subjects = toSubjectColumns(timetable);
        List<MarkSheet.Category> categories = toCategoryBands(timetable, curriculum);

        List<MarkSheet.Row> rows = buildRows(roster, timetable, enrolments, marks);
        rows = withRanks(rows);

        return new MarkSheet(
                classroom.getId(),
                ReportLayout.classLabel(classroom),
                ReportLayout.gradeName(classroom),
                classroom.getMedium(),
                nameOf(classroom.getEmployee_id()),
                term.getId(),
                term.getName(),
                classroom.getAcademic_year_id() == null ? null : classroom.getAcademic_year_id().getName(),
                LocalDateTime.now(),
                categories,
                subjects,
                rows,
                summarise(subjects, rows),
                HIGHLIGHT_AVERAGE);
    }

    /**
     * The term, checked against the class's own academic year.
     *
     * Without the check a caller could put 2026 marks under a 2025 term and the
     * sheet would render perfectly, having quietly filed a year's results in the
     * wrong place.
     */
    public Term requireTermOf(Classroom classroom, Integer termId) {
        Term term = termDao.findById(termId)
                .orElseThrow(() -> ApiException.notFound("Term " + termId + " does not exist."));

        Integer classYear = classroom.getAcademic_year_id() == null
                ? null
                : classroom.getAcademic_year_id().getId();
        Integer termYear = term.getAcademic_year_id() == null
                ? null
                : term.getAcademic_year_id().getId();

        if (classYear != null && termYear != null && !classYear.equals(termYear)) {
            throw ApiException.badRequest(term.getName() + " belongs to a different academic year than "
                    + ReportLayout.classLabel(classroom) + ".");
        }
        return term;
    }

    /** Timetable in print order: category band, then subject name. */
    public List<ClassroomSubject> orderedTimetable(Integer classroomId) {
        return orderedTimetable(classroomDao.findById(classroomId).orElse(null), classroomId);
    }

    /**
     * The class's subjects, in the order its grade's curriculum lists them.
     *
     * The sheet used to sort by each subject's own category and name, which is
     * a property of the subject rather than of the grade taking it - so grade 1
     * printed Buddhism, English, Mathematics, Science, Sinhala alphabetically,
     * inside a band headed "6-9 Core". The curriculum knows both the order the
     * school reads them in, Sinhala first, and the basket a subject sits in
     * *for this grade*, so it is what the columns follow.
     *
     * A grade with no curriculum recorded falls back to the previous ordering.
     */
    public List<ClassroomSubject> orderedTimetable(Classroom classroom, Integer classroomId) {
        List<ClassroomSubject> timetable = new ArrayList<>(classroomSubjectDao.listByClassroom(classroomId));
        Map<Integer, GradeSubject> curriculum = curriculumFor(classroom);

        if (curriculum.isEmpty()) {
            timetable.sort(Comparator.comparing(ClassroomSubject::getSubject_detail_id,
                    ReportLayout.subjectOrder()));
            return timetable;
        }

        timetable.sort(Comparator
                // A subject the grade does not take sorts last rather than
                // vanishing: it is on the timetable by mistake, and hiding it
                // would hide the mistake.
                .comparingInt((ClassroomSubject line) -> basketOrder(curriculum, line))
                .thenComparingInt(line -> curriculumPosition(curriculum, line))
                .thenComparing(line -> line.getSubject_detail_id().getName(),
                        String.CASE_INSENSITIVE_ORDER));
        return timetable;
    }

    /** The grade's curriculum, keyed by subject id. Empty when it has none. */
    private Map<Integer, GradeSubject> curriculumFor(Classroom classroom) {
        if (classroom == null || classroom.getGrade_id() == null) {
            return Map.of();
        }
        Map<Integer, GradeSubject> bySubject = new LinkedHashMap<>();
        for (GradeSubject row : gradeSubjectDao.listForGrade(classroom.getGrade_id().getId())) {
            if (row.getSubject() != null) {
                bySubject.put(row.getSubject().getId(), row);
            }
        }
        return bySubject;
    }

    /** Core first, then the numbered baskets, then General; strays last. */
    private static int basketOrder(Map<Integer, GradeSubject> curriculum, ClassroomSubject line) {
        GradeSubject row = curriculum.get(line.getSubject_detail_id().getId());
        if (row == null) {
            return Integer.MAX_VALUE;
        }
        String basket = row.getBasket() == null ? GradeSubject.CORE : row.getBasket();
        return switch (basket) {
            case GradeSubject.CORE -> 0;
            case GradeSubject.CATEGORY_1 -> 1;
            case GradeSubject.CATEGORY_2 -> 2;
            case GradeSubject.CATEGORY_3 -> 3;
            case GradeSubject.GENERAL -> 4;
            default -> 5;
        };
    }

    private static int curriculumPosition(Map<Integer, GradeSubject> curriculum, ClassroomSubject line) {
        GradeSubject row = curriculum.get(line.getSubject_detail_id().getId());
        return row == null || row.getSortOrder() == null ? Integer.MAX_VALUE : row.getSortOrder();
    }

    /**
     * Students on the roll, in the order the sheet numbers them.
     *
     * Filtered the same way as the class head count report, so the mark sheet
     * and the roll call cannot disagree about who is in the class.
     */
    public List<StudentRegistration> activeRoster(Integer classroomId) {
        return registrationDao.listByClassroom(classroomId).stream()
                .filter(MarkSheetService::isActive)
                .sorted(Comparator.comparing(
                        registration -> registration.getStudent_id() == null
                                ? ""
                                : String.valueOf(registration.getStudent_id().getFullname()),
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static boolean isActive(StudentRegistration registration) {
        if (registration.getRegistration_status_id() != null
                && !"active".equalsIgnoreCase(registration.getRegistration_status_id().getName())) {
            return false;
        }
        return registration.getStudent_id() == null
                || registration.getStudent_id().getStudent_status_id() == null
                || !"deleted".equalsIgnoreCase(registration.getStudent_id().getStudent_status_id().getName());
    }

    // ---- assembly ------------------------------------------------------------

    private Map<Integer, Map<Integer, StudentSubject>> enrolmentsByRegistration(List<StudentRegistration> roster) {
        Map<Integer, Map<Integer, StudentSubject>> byRegistration = new HashMap<>();
        for (StudentRegistration registration : roster) {
            Map<Integer, StudentSubject> byTimetableLine = new HashMap<>();
            for (StudentSubject enrolment : studentSubjectDao.listByRegistration(registration.getId())) {
                byTimetableLine.put(enrolment.getClassroom_subject_id().getId(), enrolment);
            }
            byRegistration.put(registration.getId(), byTimetableLine);
        }
        return byRegistration;
    }

    private Map<Integer, StudentMark> marksByStudentSubject(Integer classroomId, Integer termId) {
        Map<Integer, StudentMark> byEnrolment = new HashMap<>();
        for (StudentMark mark : markDao.listByClassroomAndTerm(classroomId, termId)) {
            byEnrolment.put(mark.getStudent_subject_id().getId(), mark);
        }
        return byEnrolment;
    }

    private List<MarkSheet.Subject> toSubjectColumns(List<ClassroomSubject> timetable) {
        return timetable.stream().map(line -> {
            SubjectDetail subject = line.getSubject_detail_id();
            return new MarkSheet.Subject(
                    subject.getId(),
                    line.getId(),
                    subject.getName(),
                    ReportLayout.heading(subject),
                    ReportLayout.categoryName(subject),
                    nameOf(line.getEmployee_id()));
        }).toList();
    }

    /**
     * The category headings, each with the number of columns it spans.
     *
     * The timetable is already in category order, so consecutive runs are the
     * bands; counting them here saves every renderer from working out its own
     * merge ranges.
     */
    /** How a basket reads as a column heading on the sheet. */
    private static String bandLabel(String basket) {
        if (basket == null || basket.isBlank()) {
            return GradeSubject.CORE;
        }
        return GradeSubject.GENERAL.equals(basket) ? "General (GE / GK)" : basket;
    }

    private List<MarkSheet.Category> toCategoryBands(List<ClassroomSubject> timetable,
            Map<Integer, GradeSubject> curriculum) {

        Map<String, Integer> spans = new LinkedHashMap<>();
        Map<String, Integer> ids = new LinkedHashMap<>();

        for (ClassroomSubject line : timetable) {
            SubjectDetail subject = line.getSubject_detail_id();

            // The band is the basket this grade puts the subject in, not the
            // subject's own classification. A grade 1 sheet headed "6-9 Core"
            // because Sinhala happens to be classified there is a heading about
            // the subject rather than about the class in front of the reader,
            // and it is what the school reported three times.
            GradeSubject placement = curriculum.get(subject.getId());
            String label;
            Integer id = null;

            if (placement != null) {
                label = bandLabel(placement.getBasket());
            } else if (curriculum.isEmpty()) {
                String name = ReportLayout.categoryName(subject);
                label = name.isBlank() ? "Uncategorised" : name;
                id = subject.getCategory() == null ? null : subject.getCategory().getId();
            } else {
                label = "Not on the curriculum";
            }

            spans.merge(label, 1, Integer::sum);
            ids.putIfAbsent(label, id);
        }

        return spans.entrySet().stream()
                .map(entry -> new MarkSheet.Category(ids.get(entry.getKey()), entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<MarkSheet.Row> buildRows(List<StudentRegistration> roster, List<ClassroomSubject> timetable,
            Map<Integer, Map<Integer, StudentSubject>> enrolments, Map<Integer, StudentMark> marks) {

        List<MarkSheet.Row> rows = new ArrayList<>();
        int index = 1;

        for (StudentRegistration registration : roster) {
            Map<Integer, StudentSubject> taken = enrolments.getOrDefault(registration.getId(), Map.of());

            List<MarkSheet.Cell> cells = new ArrayList<>();
            int total = 0;
            int resultsRecorded = 0;

            for (ClassroomSubject line : timetable) {
                StudentSubject enrolment = taken.get(line.getId());
                if (enrolment == null) {
                    cells.add(new MarkSheet.Cell(null, null, false, false, GradeScale.NOT_TAKEN));
                    continue;
                }

                StudentMark mark = marks.get(enrolment.getId());
                Integer value = mark == null ? null : mark.getMarks();
                boolean absent = mark != null && Boolean.TRUE.equals(mark.getAbsent());

                if (value != null || absent) {
                    resultsRecorded++;
                }
                if (value != null && !absent) {
                    total += value;
                }

                cells.add(new MarkSheet.Cell(enrolment.getId(), absent ? null : value, absent, true,
                        GradeScale.letterFor(value, absent)));
            }

            Double average = resultsRecorded == 0 ? null : round(total / (double) resultsRecorded);

            rows.add(new MarkSheet.Row(
                    index++,
                    registration.getId(),
                    registration.getStudent_id() == null ? null : registration.getStudent_id().getId(),
                    registration.getStudent_id() == null ? null : registration.getStudent_id().getStu_no(),
                    registration.getStudent_id() == null ? "—" : registration.getStudent_id().getFullname(),
                    cells,
                    total,
                    average,
                    null,
                    countLetters(cells),
                    average != null && average >= HIGHLIGHT_AVERAGE));
        }

        return rows;
    }

    /**
     * Competition ranking on average: equal averages share a place and the next
     * distinct average skips the places used up, which is what Excel's RANK
     * does and what the printed sheets show.
     *
     * A student with no subjects has no average and so no rank, rather than
     * being ranked last on a total of zero.
     */
    private List<MarkSheet.Row> withRanks(List<MarkSheet.Row> rows) {
        List<MarkSheet.Row> ordered = new ArrayList<>(rows);
        ordered.sort(Comparator.comparing(MarkSheet.Row::average,
                Comparator.nullsLast(Comparator.reverseOrder())));

        Map<Integer, Integer> rankByRow = new HashMap<>();
        Double previous = null;
        int rank = 0;
        int seen = 0;

        for (MarkSheet.Row row : ordered) {
            if (row.average() == null) {
                continue;
            }
            seen++;
            if (previous == null || !previous.equals(row.average())) {
                rank = seen;
                previous = row.average();
            }
            rankByRow.put(row.index(), rank);
        }

        return rows.stream()
                .map(row -> new MarkSheet.Row(row.index(), row.registrationId(), row.studentId(),
                        row.admissionNo(), row.studentName(), row.cells(), row.total(), row.average(),
                        rankByRow.get(row.index()), row.gradeCounts(), row.highlight()))
                .toList();
    }

    private List<MarkSheet.LetterCount> countLetters(List<MarkSheet.Cell> cells) {
        return GradeScale.LETTERS.stream()
                .map(letter -> new MarkSheet.LetterCount(letter,
                        (int) cells.stream().filter(cell -> letter.equals(cell.grade())).count()))
                .toList();
    }

    /**
     * The block printed under the roster: per subject, how many of each letter
     * it awarded and how many marks fell in each band.
     */
    private List<MarkSheet.SubjectSummary> summarise(List<MarkSheet.Subject> subjects, List<MarkSheet.Row> rows) {
        List<MarkSheet.SubjectSummary> summary = new ArrayList<>();

        for (int column = 0; column < subjects.size(); column++) {
            final int index = column;
            List<MarkSheet.Cell> cells = rows.stream()
                    .map(row -> row.cells().get(index))
                    .filter(MarkSheet.Cell::enrolled)
                    .toList();

            List<MarkSheet.LetterCount> letters = GradeScale.LETTERS.stream()
                    .map(letter -> new MarkSheet.LetterCount(letter,
                            (int) cells.stream().filter(cell -> letter.equals(cell.grade())).count()))
                    .toList();

            List<MarkSheet.BandCount> bands = GradeScale.BANDS.stream()
                    .map(band -> new MarkSheet.BandCount(band.label(),
                            (int) cells.stream()
                                    .filter(cell -> cell.marks() != null && band.contains(cell.marks()))
                                    .count()))
                    .toList();

            int absent = (int) cells.stream().filter(MarkSheet.Cell::absent).count();
            int recorded = (int) cells.stream()
                    .filter(cell -> cell.marks() != null || cell.absent())
                    .count();

            summary.add(new MarkSheet.SubjectSummary(subjects.get(index).subjectId(),
                    subjects.get(index).name(), letters, bands, absent, recorded));
        }

        return summary;
    }

    // -------------------------------------------------------------------------

    private static String nameOf(Employee employee) {
        return employee == null ? null : employee.getFullname();
    }

    /** One decimal place, as the sheets print averages. */
    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
