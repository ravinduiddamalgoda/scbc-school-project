package com.scbck.service;

import static com.scbck.service.ReportLayout.classroomOrder;
import static com.scbck.service.ReportLayout.document;
import static com.scbck.service.ReportLayout.gradeName;
import static com.scbck.service.ReportLayout.groupByBand;
import static com.scbck.service.ReportLayout.heading;
import static com.scbck.service.ReportLayout.levelOrder;
import static com.scbck.service.ReportLayout.orderedBands;
import static com.scbck.service.ReportLayout.orderedSubjects;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.scbck.dto.ReportColumn;
import com.scbck.dto.ReportDocument;
import com.scbck.dto.ReportSection;
import com.scbck.model.AcademicYear;
import com.scbck.model.Classroom;
import com.scbck.model.ClassroomSubject;
import com.scbck.model.GradeHead;
import com.scbck.model.SubjectDetail;
import com.scbck.repository.ClassroomDao;
import com.scbck.repository.ClassroomSubjectDao;
import com.scbck.repository.GradeDao;
import com.scbck.repository.GradeHeadDao;
import com.scbck.repository.StudentRegistrationDao;
import com.scbck.repository.StudentSubjectDao;
import com.scbck.repository.projection.CountByKey;

/**
 * The reports that describe how the school is arranged: who teaches what, how
 * many sit where, and in which medium.
 *
 * Each is assembled from a couple of bulk reads and then aggregated in memory.
 * A whole school is a few thousand rows, so a report is one round trip rather
 * than one per class.
 *
 * The numbers are derived, never stored. The old workbooks held counts that had
 * to be retyped whenever a student moved class; here moving a student rewrites
 * every report that mentions them.
 */
@Service
public class ClassReports {

    private final ClassroomDao classroomDao;
    private final ClassroomSubjectDao classroomSubjectDao;
    private final StudentRegistrationDao registrationDao;
    private final StudentSubjectDao studentSubjectDao;
    private final GradeHeadDao gradeHeadDao;
    private final GradeDao gradeDao;

    public ClassReports(ClassroomDao classroomDao, ClassroomSubjectDao classroomSubjectDao,
            StudentRegistrationDao registrationDao, StudentSubjectDao studentSubjectDao,
            GradeHeadDao gradeHeadDao, GradeDao gradeDao) {
        this.classroomDao = classroomDao;
        this.classroomSubjectDao = classroomSubjectDao;
        this.registrationDao = registrationDao;
        this.studentSubjectDao = studentSubjectDao;
        this.gradeHeadDao = gradeHeadDao;
        this.gradeDao = gradeDao;
    }

    // ---- Class Teachers -----------------------------------------------------

    public ReportDocument classTeachers(AcademicYear year) {
        List<Classroom> classrooms = classroomDao.listByAcademicYear(year.getId());

        List<ReportColumn> columns = List.of(
                ReportColumn.text("Grade"),
                ReportColumn.text("Class"),
                ReportColumn.wide("Class Teacher"),
                ReportColumn.text("Staff No."));

        List<ReportSection> sections = new ArrayList<>();

        for (Map.Entry<GradeBand, List<Classroom>> band : groupByBand(classrooms).entrySet()) {
            List<List<String>> rows = new ArrayList<>();
            int assigned = 0;

            for (Classroom classroom : band.getValue()) {
                var teacher = classroom.getEmployee_id();
                if (teacher != null) {
                    assigned++;
                }
                rows.add(List.of(
                        gradeName(classroom),
                        classroom.getName(),
                        teacher == null ? "Not assigned" : teacher.getFullname(),
                        teacher == null || teacher.getEmp_no() == null ? "" : teacher.getEmp_no()));
            }

            sections.add(new ReportSection(
                    band.getKey().title(),
                    null,
                    columns,
                    rows,
                    List.of("Total", rows.size() + " class(es)", assigned + " assigned", "")));
        }

        return document(ReportService.CLASS_TEACHERS, "Class Teachers",
                "Every class in the year and the teacher responsible for it.",
                year, ReportLayout.PORTRAIT, sections);
    }

    // ---- Student Count of Classes -------------------------------------------

    public ReportDocument studentCounts(AcademicYear year) {
        List<Classroom> classrooms = classroomDao.listByAcademicYear(year.getId());
        Map<Integer, Long> counts = CountByKey.toMap(registrationDao.countActiveByClassroom(year.getId()));

        List<ReportColumn> columns = List.of(
                ReportColumn.text("Grade"),
                ReportColumn.text("Class"),
                ReportColumn.wide("Class Teacher"),
                ReportColumn.number("Students"));

        List<ReportSection> sections = new ArrayList<>();

        for (Map.Entry<GradeBand, List<Classroom>> band : groupByBand(classrooms).entrySet()) {
            List<List<String>> rows = new ArrayList<>();
            long total = 0;

            for (Classroom classroom : band.getValue()) {
                long count = counts.getOrDefault(classroom.getId(), 0L);
                total += count;

                rows.add(List.of(
                        gradeName(classroom),
                        classroom.getName(),
                        classroom.getEmployee_id() == null ? "Not assigned" : classroom.getEmployee_id().getFullname(),
                        String.valueOf(count)));
            }

            sections.add(new ReportSection(
                    band.getKey().title(),
                    null,
                    columns,
                    rows,
                    List.of("Total", rows.size() + " class(es)", "", String.valueOf(total))));
        }

        return document(ReportService.STUDENT_COUNTS, "Student Count of Classes",
                "How many students are on the roll of each class.",
                year, ReportLayout.PORTRAIT, sections);
    }

    // ---- Subject Wise Teachers ----------------------------------------------

    /**
     * Grades down the side, subjects across the top, the number of distinct
     * teachers taking that subject in that grade in the cell.
     *
     * Distinct is the point: a subject taught to all seven classes of a grade by
     * the same teacher counts once, which is the figure the staffing decision
     * actually turns on.
     */
    public ReportDocument subjectTeachers(AcademicYear year) {
        List<ClassroomSubject> timetable = classroomSubjectDao.listByAcademicYear(year.getId());

        // band -> grade name -> subject id -> distinct teacher ids
        Map<GradeBand, Map<String, Map<Integer, Set<Integer>>>> byBand = new LinkedHashMap<>();
        Map<GradeBand, Map<Integer, SubjectDetail>> subjectsByBand = new LinkedHashMap<>();
        Map<GradeBand, Map<String, Integer>> gradeOrder = new LinkedHashMap<>();

        for (ClassroomSubject line : timetable) {
            Classroom classroom = line.getClassroom_id();
            GradeBand band = GradeBand.of(classroom.getGrade_id());
            SubjectDetail subject = line.getSubject_detail_id();
            String grade = gradeName(classroom);

            subjectsByBand.computeIfAbsent(band, key -> new LinkedHashMap<>())
                    .putIfAbsent(subject.getId(), subject);
            gradeOrder.computeIfAbsent(band, key -> new LinkedHashMap<>())
                    .putIfAbsent(grade, levelOrder(classroom));

            Set<Integer> teachers = byBand
                    .computeIfAbsent(band, key -> new LinkedHashMap<>())
                    .computeIfAbsent(grade, key -> new LinkedHashMap<>())
                    .computeIfAbsent(subject.getId(), key -> new LinkedHashSet<>());

            if (line.getEmployee_id() != null) {
                teachers.add(line.getEmployee_id().getId());
            }
        }

        List<ReportSection> sections = new ArrayList<>();

        for (GradeBand band : orderedBands(byBand.keySet())) {
            List<SubjectDetail> subjects = orderedSubjects(subjectsByBand.get(band).values());
            List<ReportColumn> columns = new ArrayList<>();
            columns.add(ReportColumn.text("Grade"));
            subjects.forEach(subject -> columns.add(ReportColumn.number(heading(subject))));

            Map<String, Map<Integer, Set<Integer>>> grades = byBand.get(band);
            List<String> gradeNames = new ArrayList<>(grades.keySet());
            gradeNames.sort(Comparator.comparing(name -> gradeOrder.get(band).get(name)));

            List<List<String>> rows = new ArrayList<>();
            for (String grade : gradeNames) {
                List<String> row = new ArrayList<>();
                row.add(grade);
                for (SubjectDetail subject : subjects) {
                    Set<Integer> teachers = grades.get(grade).get(subject.getId());
                    // Blank, not zero: the subject is simply not on this grade's
                    // timetable, which is a different fact from "nobody teaches it".
                    row.add(teachers == null ? "" : String.valueOf(teachers.size()));
                }
                rows.add(row);
            }

            List<String> footer = new ArrayList<>();
            footer.add("Total");
            for (SubjectDetail subject : subjects) {
                Set<Integer> distinct = new HashSet<>();
                for (Map<Integer, Set<Integer>> perGrade : grades.values()) {
                    Set<Integer> teachers = perGrade.get(subject.getId());
                    if (teachers != null) {
                        distinct.addAll(teachers);
                    }
                }
                footer.add(String.valueOf(distinct.size()));
            }

            sections.add(new ReportSection(band.title(),
                    "Distinct teachers per subject", columns, rows, footer));
        }

        return document(ReportService.SUBJECT_TEACHERS, "Subject Wise Teachers",
                "How many teachers take each subject, grade by grade.",
                year, ReportLayout.LANDSCAPE, sections);
    }

    // ---- Subject wise Student Count of Classes ------------------------------

    public ReportDocument subjectStudentCounts(AcademicYear year) {
        List<ClassroomSubject> timetable = classroomSubjectDao.listByAcademicYear(year.getId());
        Map<Integer, Long> counts = CountByKey.toMap(studentSubjectDao.countActiveByClassroomSubject(year.getId()));

        // band -> classroom id -> subject id -> students taking it.
        // Keyed by id, not by the entity: Lombok's @Data equals/hashCode on
        // Classroom walks into the class teacher and hashes their photo.
        Map<GradeBand, Map<Integer, Map<Integer, Long>>> byBand = new LinkedHashMap<>();
        Map<GradeBand, Map<Integer, SubjectDetail>> subjectsByBand = new LinkedHashMap<>();
        Map<Integer, Classroom> classroomsById = new LinkedHashMap<>();

        for (ClassroomSubject line : timetable) {
            Classroom classroom = line.getClassroom_id();
            GradeBand band = GradeBand.of(classroom.getGrade_id());
            SubjectDetail subject = line.getSubject_detail_id();

            classroomsById.putIfAbsent(classroom.getId(), classroom);
            subjectsByBand.computeIfAbsent(band, key -> new LinkedHashMap<>())
                    .putIfAbsent(subject.getId(), subject);

            byBand.computeIfAbsent(band, key -> new LinkedHashMap<>())
                    .computeIfAbsent(classroom.getId(), key -> new LinkedHashMap<>())
                    .put(subject.getId(), counts.getOrDefault(line.getId(), 0L));
        }

        List<ReportSection> sections = new ArrayList<>();

        for (GradeBand band : orderedBands(byBand.keySet())) {
            List<SubjectDetail> subjects = orderedSubjects(subjectsByBand.get(band).values());

            List<ReportColumn> columns = new ArrayList<>();
            columns.add(ReportColumn.text("Grade"));
            columns.add(ReportColumn.text("Class"));
            subjects.forEach(subject -> columns.add(ReportColumn.number(heading(subject))));

            List<List<String>> rows = new ArrayList<>();
            long[] totals = new long[subjects.size()];

            Map<Integer, Map<Integer, Long>> perClassroom = byBand.get(band);
            List<Classroom> classrooms = perClassroom.keySet().stream()
                    .map(classroomsById::get)
                    .sorted(classroomOrder())
                    .toList();

            for (Classroom classroom : classrooms) {
                Map<Integer, Long> takenBySubject = perClassroom.get(classroom.getId());

                List<String> row = new ArrayList<>();
                row.add(gradeName(classroom));
                row.add(classroom.getName());

                for (int index = 0; index < subjects.size(); index++) {
                    Long taken = takenBySubject.get(subjects.get(index).getId());
                    if (taken == null) {
                        row.add("");
                    } else {
                        totals[index] += taken;
                        row.add(String.valueOf(taken));
                    }
                }
                rows.add(row);
            }

            List<String> footer = new ArrayList<>();
            footer.add("Total");
            footer.add(rows.size() + " class(es)");
            for (long total : totals) {
                footer.add(String.valueOf(total));
            }

            sections.add(new ReportSection(band.title(),
                    "Students taking each subject", columns, rows, footer));
        }

        return document(ReportService.SUBJECT_STUDENT_COUNTS, "Subject wise Student Count of Classes",
                "How many students in each class take each subject.",
                year, ReportLayout.LANDSCAPE, sections);
    }

    // ---- Medium wise Student Count of Classes -------------------------------

    /**
     * Head count by medium of instruction.
     *
     * The A/L band is grouped by stream rather than by grade, exactly as the
     * source workbook is: at that level "MATHS" and "COMMERCE" are the units
     * the school thinks in, and Grade 12 and 13 of the same stream are counted
     * together.
     */
    public ReportDocument mediumCounts(AcademicYear year) {
        List<Classroom> classrooms = classroomDao.listByAcademicYear(year.getId());
        Map<Integer, Long> counts = CountByKey.toMap(registrationDao.countActiveByClassroom(year.getId()));

        List<ReportColumn> columns = List.of(
                ReportColumn.text("Grade"),
                ReportColumn.number("Sinhala"),
                ReportColumn.number("English"),
                ReportColumn.number("Not set"),
                ReportColumn.number("Total"));

        List<ReportSection> sections = new ArrayList<>();

        for (Map.Entry<GradeBand, List<Classroom>> band : groupByBand(classrooms).entrySet()) {
            boolean byStream = band.getKey().from() >= 12;

            // Row label -> [sinhala, english, unset]. Insertion-ordered because
            // groupByBand already sorted the classes into reading order.
            Map<String, long[]> rows = new LinkedHashMap<>();

            for (Classroom classroom : band.getValue()) {
                String label = byStream ? classroom.getName() : gradeName(classroom);
                long[] tally = rows.computeIfAbsent(label, key -> new long[3]);
                tally[mediumColumn(classroom.getMedium())] += counts.getOrDefault(classroom.getId(), 0L);
            }

            List<List<String>> body = new ArrayList<>();
            long[] bandTotals = new long[3];

            for (Map.Entry<String, long[]> row : rows.entrySet()) {
                long[] tally = row.getValue();
                for (int index = 0; index < 3; index++) {
                    bandTotals[index] += tally[index];
                }
                body.add(List.of(
                        row.getKey(),
                        String.valueOf(tally[0]),
                        String.valueOf(tally[1]),
                        tally[2] == 0 ? "" : String.valueOf(tally[2]),
                        String.valueOf(tally[0] + tally[1] + tally[2])));
            }

            sections.add(new ReportSection(
                    band.getKey().title(),
                    byStream ? "Grouped by stream" : null,
                    columns,
                    body,
                    List.of("Total",
                            String.valueOf(bandTotals[0]),
                            String.valueOf(bandTotals[1]),
                            bandTotals[2] == 0 ? "" : String.valueOf(bandTotals[2]),
                            String.valueOf(bandTotals[0] + bandTotals[1] + bandTotals[2]))));
        }

        return document(ReportService.MEDIUM_COUNTS, "Medium wise Student Count of Classes",
                "How many students sit in each medium of instruction.",
                year, ReportLayout.PORTRAIT, sections);
    }

    // ---- Grade Heads --------------------------------------------------------

    /**
     * Every grade and the teacher heading it.
     *
     * Grades with nobody named are listed too - a gap in the roster is the
     * thing this report exists to surface, so hiding those rows would defeat it.
     */
    public ReportDocument gradeHeads(AcademicYear year) {
        Map<Integer, GradeHead> assigned = new LinkedHashMap<>();
        for (GradeHead head : gradeHeadDao.listByAcademicYear(year.getId())) {
            assigned.put(head.getGrade_id().getId(), head);
        }

        List<ReportColumn> columns = List.of(
                ReportColumn.text("Grade"),
                ReportColumn.wide("Grade Head"),
                ReportColumn.text("Staff No."));

        List<List<String>> rows = new ArrayList<>();
        int named = 0;

        for (var grade : gradeDao.findAll(org.springframework.data.domain.Sort.by("id"))) {
            GradeHead head = assigned.get(grade.getId());
            if (head != null) {
                named++;
            }
            rows.add(List.of(
                    grade.getName(),
                    head == null ? "Not assigned" : head.getEmployee_id().getFullname(),
                    head == null || head.getEmployee_id().getEmp_no() == null
                            ? ""
                            : head.getEmployee_id().getEmp_no()));
        }

        ReportSection section = new ReportSection("All grades", null, columns, rows,
                List.of("Total", named + " of " + rows.size() + " assigned", ""));

        return document(ReportService.GRADE_HEADS, "Grade Heads",
                "The teacher responsible for each grade.",
                year, ReportLayout.PORTRAIT, List.of(section));
    }

    // -------------------------------------------------------------------------

    /** 0 = Sinhala, 1 = English, 2 = medium not recorded on the class. */
    private int mediumColumn(String medium) {
        if (medium == null || medium.isBlank()) {
            return 2;
        }
        return medium.trim().toLowerCase().startsWith("e") ? 1 : 0;
    }
}
