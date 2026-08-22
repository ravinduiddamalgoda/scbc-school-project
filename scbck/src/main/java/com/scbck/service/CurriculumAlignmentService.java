package com.scbck.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scbck.dto.CurriculumAlignment;
import com.scbck.exception.ApiException;
import com.scbck.model.AcademicYear;
import com.scbck.model.Classroom;
import com.scbck.model.ClassroomSubject;
import com.scbck.model.GradeSubject;
import com.scbck.model.SubjectDetail;
import com.scbck.repository.ClassroomDao;
import com.scbck.repository.ClassroomSubjectDao;
import com.scbck.repository.GradeSubjectDao;
import com.scbck.repository.StudentMarkDao;
import com.scbck.repository.StudentSubjectDao;

/**
 * Brings class timetables into line with the curriculum.
 *
 * The curriculum said what each grade is taught, but nothing ever applied it to
 * the classes that already existed. The sample data had given every class a
 * more or less arbitrary set of subjects, so grade 1 classes carried Combined
 * Maths and Chemistry - which then appeared on the grade 1 mark sheet, because
 * the mark sheet's columns come from the timetable and nowhere else.
 *
 * Fixing eighty-five classes by hand was not a reasonable answer, so this does
 * it in one pass, and reports what it will destroy before destroying it.
 */
@Service
public class CurriculumAlignmentService {

    private final ClassroomDao classroomDao;
    private final ClassroomSubjectDao classroomSubjectDao;
    private final GradeSubjectDao gradeSubjectDao;
    private final StudentSubjectDao studentSubjectDao;
    private final StudentMarkDao studentMarkDao;

    public CurriculumAlignmentService(ClassroomDao classroomDao,
            ClassroomSubjectDao classroomSubjectDao, GradeSubjectDao gradeSubjectDao,
            StudentSubjectDao studentSubjectDao, StudentMarkDao studentMarkDao) {
        this.classroomDao = classroomDao;
        this.classroomSubjectDao = classroomSubjectDao;
        this.gradeSubjectDao = gradeSubjectDao;
        this.studentSubjectDao = studentSubjectDao;
        this.studentMarkDao = studentMarkDao;
    }

    /**
     * Aligns every class of one year, or a single class.
     *
     * @param classroomId one class, or null for every class in the year
     * @param dryRun      true to report the difference without applying it
     * @param force       true to proceed even though marks would be destroyed
     */
    @Transactional
    public CurriculumAlignment align(AcademicYear year, Integer classroomId, boolean dryRun,
            boolean force) {

        List<Classroom> classrooms = classroomId == null
                ? classroomDao.listByAcademicYear(year.getId())
                : List.of(classroomDao.findById(classroomId).orElseThrow(
                        () -> ApiException.notFound("Class " + classroomId + " does not exist.")));

        Map<Integer, List<GradeSubject>> curriculum = curriculumByGrade();

        List<CurriculumAlignment.ClassChange> changes = new ArrayList<>();
        int added = 0;
        int removed = 0;
        long marks = 0;

        for (Classroom classroom : classrooms) {
            if (classroom.getGrade_id() == null) {
                continue;
            }

            List<GradeSubject> planned = curriculum.getOrDefault(classroom.getGrade_id().getId(), List.of());
            if (planned.isEmpty()) {
                // A grade with no curriculum recorded is left alone. Treating
                // "not set up" as "takes nothing" would empty its timetable.
                continue;
            }

            List<ClassroomSubject> existing = classroomSubjectDao.listByClassroom(classroom.getId());

            Set<Integer> wanted = new LinkedHashSet<>();
            planned.forEach(row -> wanted.add(row.getSubject().getId()));

            List<ClassroomSubject> toRemove = existing.stream()
                    .filter(line -> !wanted.contains(line.getSubject_detail_id().getId()))
                    .toList();

            Set<Integer> alreadyThere = new LinkedHashSet<>();
            existing.forEach(line -> alreadyThere.add(line.getSubject_detail_id().getId()));

            List<GradeSubject> toAdd = planned.stream()
                    .filter(row -> !alreadyThere.contains(row.getSubject().getId()))
                    .toList();

            if (toRemove.isEmpty() && toAdd.isEmpty()) {
                continue;
            }

            long marksHere = toRemove.isEmpty()
                    ? 0
                    : studentMarkDao.countForClassroomSubjects(
                            toRemove.stream().map(ClassroomSubject::getId).toList());

            changes.add(new CurriculumAlignment.ClassChange(
                    classroom.getId(),
                    classroom.getName(),
                    classroom.getGrade_id().getName(),
                    toAdd.stream().map(row -> row.getSubject().getName()).toList(),
                    toRemove.stream().map(line -> line.getSubject_detail_id().getName()).toList(),
                    marksHere));

            added += toAdd.size();
            removed += toRemove.size();
            marks += marksHere;

            if (!dryRun) {
                apply(classroom, toAdd, toRemove);
            }
        }

        // Checked after the loop so the report is complete either way: the
        // school sees every class that would be affected, not just the first
        // one that tripped the guard.
        if (!dryRun && marks > 0 && !force) {
            throw ApiException.conflict("Aligning these classes would delete " + marks
                    + " recorded mark(s), because the subjects being removed have marks against"
                    + " them. Review the list and confirm if that is intended.");
        }

        return new CurriculumAlignment(dryRun, classrooms.size(), changes.size(),
                added, removed, marks, changes);
    }

    /**
     * Aligns only the classes where nothing can be lost by doing so.
     *
     * A class whose corrections destroy no marks has nothing at stake: its
     * timetable is simply wrong, and leaving it wrong helps nobody. Those are
     * corrected without being asked. A class where marks would go is left
     * exactly as it is, for somebody to decide about through the dialog.
     *
     * This exists because the manual route did not reach the school. They
     * reported grade 1 classes carrying A/L subjects three times, and the
     * correction sat behind a button on a screen they had no reason to open.
     * A fix nobody finds is not a fix.
     */
    @Transactional
    public CurriculumAlignment alignSafeClasses(AcademicYear year) {
        CurriculumAlignment preview = align(year, null, true, false);

        int changed = 0;
        int added = 0;
        int removed = 0;

        for (CurriculumAlignment.ClassChange change : preview.changes()) {
            if (change.marksAffected() > 0) {
                continue;
            }
            align(year, change.classroomId(), false, false);
            changed++;
            added += change.added().size();
            removed += change.removed().size();
        }

        // The marks figure carried forward is what was *skipped*, so the caller
        // can say how much still needs a human decision.
        return new CurriculumAlignment(false, preview.classesConsidered(), changed, added, removed,
                preview.marksAffected(), preview.changes());
    }

    // -------------------------------------------------------------------------

    private void apply(Classroom classroom, List<GradeSubject> toAdd,
            List<ClassroomSubject> toRemove) {

        if (!toRemove.isEmpty()) {
            List<Integer> ids = toRemove.stream().map(ClassroomSubject::getId).toList();
            // Deepest dependant first, as in the timetable editor: marks point
            // at enrolments, which point at these lines.
            studentMarkDao.deleteByClassroomSubjectIds(ids);
            studentSubjectDao.deleteByClassroomSubjectIds(ids);
            classroomSubjectDao.deleteAll(toRemove);
        }

        List<ClassroomSubject> created = new ArrayList<>();
        for (GradeSubject row : toAdd) {
            ClassroomSubject line = new ClassroomSubject();
            line.setClassroom_id(classroom);
            line.setSubject_detail_id(row.getSubject());
            // No teacher: the curriculum says what is taught, not by whom, and
            // guessing would put a name against a class nobody assigned.
            created.add(line);
        }
        classroomSubjectDao.saveAll(created);
    }

    private Map<Integer, List<GradeSubject>> curriculumByGrade() {
        Map<Integer, List<GradeSubject>> byGrade = new LinkedHashMap<>();
        for (GradeSubject row : gradeSubjectDao.listAll()) {
            if (row.getGrade() == null || row.getSubject() == null) {
                continue;
            }
            SubjectDetail subject = row.getSubject();
            if (subject.getId() == null) {
                continue;
            }
            byGrade.computeIfAbsent(row.getGrade().getId(), key -> new ArrayList<>()).add(row);
        }
        return byGrade;
    }
}
