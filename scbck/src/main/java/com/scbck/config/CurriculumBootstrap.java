package com.scbck.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.scbck.model.Grade;
import com.scbck.model.GradeSubject;
import com.scbck.model.SubjectCategory;
import com.scbck.model.SubjectDetail;
import com.scbck.repository.GradeDao;
import com.scbck.repository.GradeSubjectDao;
import com.scbck.repository.SubjectCategoryDao;
import com.scbck.repository.SubjectDetailDao;

/**
 * Installs the school's curriculum: the examination categories subjects are
 * classified under, and the subjects each grade is actually taught.
 *
 * Both were previously assumptions rather than data. The subject list shipped
 * with seven invented groupings ("Optional", "Aesthetic", "Language") that the
 * school does not use, and no grade had a subject list at all - every class
 * from grade 1 to grade 13 was offered all twenty-nine subjects on its
 * timetable. This runs after {@link SubjectCategoryBackfill} has converted the
 * old free-text column, and replaces those guesses with the real thing.
 *
 * Everything here is additive and idempotent:
 *
 * <ul>
 *   <li>a category or subject is created only when no row of that name exists;
 *   <li>a subject's category is set only when it does not already have one, so
 *       a reclassification done in the Subjects screen survives a restart;
 *   <li>a grade's curriculum is seeded only when that grade has no subjects at
 *       all, so a curriculum edited in Academic setup is never overwritten.
 * </ul>
 *
 * The upshot is that a school can change any of it and this class will leave
 * the change alone; it only ever fills in what is missing.
 */
@Component
@Order(30)
public class CurriculumBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CurriculumBootstrap.class);

    // ---- Categories ---------------------------------------------------------

    private record CategorySeed(String name, int sortOrder, int gradeFrom, int gradeTo,
            Integer expected) {
    }

    /**
     * The examination categories, in the order the school's requirements note
     * lists them.
     *
     * The note begins at "6-9 Core" because that is where the Department's
     * categories begin, but grades 1 to 5 still need somewhere for their
     * subjects to sit - the Classes, Marks and Reports modules all ask for a
     * grade 1 subject list. The two primary bands are therefore added ahead of
     * the note's eleven rather than leaving a third of the school
     * uncategorised.
     */
    private static final List<CategorySeed> CATEGORIES = List.of(
            new CategorySeed("Grades 1-2 Core", 10, 1, 2, 5),
            new CategorySeed("Grades 3-5 Core", 20, 3, 5, 7),
            new CategorySeed("6-9 Core", 30, 6, 9, 12),
            new CategorySeed("6-9 Cat 1", 40, 6, 9, 1),
            new CategorySeed("O/L Core", 50, 10, 11, 7),
            new CategorySeed("O/L Cat 1", 60, 10, 11, 1),
            new CategorySeed("O/L Cat 2", 70, 10, 11, 1),
            new CategorySeed("O/L Cat 3", 80, 10, 11, 1),
            new CategorySeed("A/L Science", 90, 12, 13, 3),
            new CategorySeed("A/L Commerce", 100, 12, 13, 3),
            new CategorySeed("A/L Arts", 110, 12, 13, 3),
            new CategorySeed("A/L Other (Econ /Agriculture /IT)", 120, 12, 13, null),
            new CategorySeed("A/L Common (GIT/GK)", 130, 12, 13, 2));

    /**
     * Groupings the application invented before the school said what it uses.
     *
     * Retired rather than deleted: a category some subject still points at must
     * keep existing or that subject loses its grouping, so these are only
     * deactivated, and only once nothing references them.
     */
    private static final List<String> SUPERSEDED = List.of(
            "Core", "Optional", "Aesthetic", "Language",
            "Category 1", "Category 2", "Category 3");

    // ---- Subjects -----------------------------------------------------------

    private record SubjectSeed(String name, String code) {
    }

    /** Subjects the curriculum needs that the original seed did not create. */
    private static final List<SubjectSeed> SUBJECTS = List.of(
            new SubjectSeed("Environment Science", "Env. Sci."),
            new SubjectSeed("IT", null),
            new SubjectSeed("Commerce", null),
            new SubjectSeed("General English", "GE"),
            new SubjectSeed("General Knowledge", "GK"),
            new SubjectSeed("GIT", null));

    /**
     * Names the seed used that the school's note spells differently.
     *
     * Renamed rather than duplicated: these are the same subject, and creating
     * "Mathematics" alongside an existing "Maths" would split its timetable
     * lines, marks and teacher counts across two rows.
     */
    private static final Map<String, String> RENAMES = Map.of(
            "Maths", "Mathematics",
            "Business", "Business Studies");

    /** Default classification, applied only to subjects with no category yet. */
    private static final Map<String, String> DEFAULT_CATEGORY = defaultCategories();

    private static Map<String, String> defaultCategories() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String name : List.of("Sinhala", "Buddhism", "Mathematics", "Science", "History",
                "English", "Civics", "Geography", "PTS", "Health", "ICT", "Tamil")) {
            map.put(name, "6-9 Core");
        }
        for (String name : List.of("Art", "Music", "Dancing")) {
            map.put(name, "6-9 Cat 1");
        }
        map.put("Environment Science", "Grades 3-5 Core");
        map.put("IT", "Grades 3-5 Core");
        for (String name : List.of("Commerce", "Japanese", "Chinese")) {
            map.put(name, "O/L Cat 1");
        }
        for (String name : List.of("Drama", "English Literature")) {
            map.put(name, "O/L Cat 2");
        }
        for (String name : List.of("Physics", "Chemistry", "Biology", "Combined Maths")) {
            map.put(name, "A/L Science");
        }
        for (String name : List.of("Accounts", "Business Studies")) {
            map.put(name, "A/L Commerce");
        }
        for (String name : List.of("Media", "Korean")) {
            map.put(name, "A/L Arts");
        }
        for (String name : List.of("Economics", "Agriculture")) {
            map.put(name, "A/L Other (Econ /Agriculture /IT)");
        }
        for (String name : List.of("GIT", "General Knowledge", "General English")) {
            map.put(name, "A/L Common (GIT/GK)");
        }
        return map;
    }

    // ---- Curriculum ---------------------------------------------------------

    private record BasketSeed(List<Integer> grades, String basket, boolean classTeacher,
            List<String> subjects) {
    }

    /**
     * Which subjects each grade band is taught.
     *
     * {@code classTeacher} marks the primary subjects the class teacher takes
     * rather than a subject teacher: in grades 1 to 5 that is Sinhala,
     * Mathematics, Environment Science and Buddhism, which is why the Subject
     * Wise Teachers report must show exactly one teacher per class for them and
     * a free count for English, Tamil and IT.
     */
    private static final List<BasketSeed> CURRICULUM = List.of(
            // Grades 1-2 - five subjects, four of them the class teacher's.
            new BasketSeed(List.of(1, 2), GradeSubject.CORE, true,
                    List.of("Sinhala", "Mathematics", "Environment Science", "Buddhism")),
            new BasketSeed(List.of(1, 2), GradeSubject.CORE, false,
                    List.of("English")),

            // Grades 3-5 - the same four, plus three taken by subject teachers.
            new BasketSeed(List.of(3, 4, 5), GradeSubject.CORE, true,
                    List.of("Sinhala", "Mathematics", "Environment Science", "Buddhism")),
            new BasketSeed(List.of(3, 4, 5), GradeSubject.CORE, false,
                    List.of("English", "Tamil", "IT")),

            // Grades 6-9 - twelve compulsory plus one aesthetic subject.
            new BasketSeed(List.of(6, 7, 8, 9), GradeSubject.CORE, false,
                    List.of("Sinhala", "Buddhism", "Mathematics", "Science", "History", "English",
                            "Civics", "Geography", "PTS", "Health", "ICT", "Tamil")),
            new BasketSeed(List.of(6, 7, 8, 9), GradeSubject.CATEGORY_1, false,
                    List.of("Art", "Music", "Dancing")),

            // Grades 10-11 - seven compulsory plus one from each of two baskets.
            new BasketSeed(List.of(10, 11), GradeSubject.CORE, false,
                    List.of("Sinhala", "Buddhism", "Mathematics", "Science", "History", "English",
                            "ICT")),
            new BasketSeed(List.of(10, 11), GradeSubject.CATEGORY_1, false,
                    List.of("Geography", "Commerce", "Tamil", "Japanese", "Chinese")),
            new BasketSeed(List.of(10, 11), GradeSubject.CATEGORY_2, false,
                    List.of("Art", "Music", "Dancing", "Drama", "English Literature")),

            // Grades 12-13 - three baskets and the subjects everyone sits.
            new BasketSeed(List.of(12, 13), GradeSubject.CATEGORY_1, false,
                    List.of("Physics", "Accounts", "Sinhala", "Geography", "Media", "Agriculture")),
            new BasketSeed(List.of(12, 13), GradeSubject.CATEGORY_2, false,
                    List.of("Biology", "Combined Maths", "Business Studies", "Art", "Music",
                            "Dancing", "Drama")),
            new BasketSeed(List.of(12, 13), GradeSubject.CATEGORY_3, false,
                    List.of("ICT", "Chemistry", "Chinese", "Japanese", "Korean")),
            new BasketSeed(List.of(12, 13), GradeSubject.GENERAL, false,
                    List.of("General English", "General Knowledge", "GIT")));

    private final SubjectCategoryDao categoryDao;
    private final SubjectDetailDao subjectDao;
    private final GradeDao gradeDao;
    private final GradeSubjectDao gradeSubjectDao;

    public CurriculumBootstrap(SubjectCategoryDao categoryDao, SubjectDetailDao subjectDao,
            GradeDao gradeDao, GradeSubjectDao gradeSubjectDao) {
        this.categoryDao = categoryDao;
        this.subjectDao = subjectDao;
        this.gradeDao = gradeDao;
        this.gradeSubjectDao = gradeSubjectDao;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, SubjectCategory> categories = ensureCategories();
        Map<String, SubjectDetail> subjects = ensureSubjects();
        classify(subjects, categories);
        retireSuperseded();
        ensureCurriculum(subjects);
    }

    // ---- Steps --------------------------------------------------------------

    private Map<String, SubjectCategory> ensureCategories() {
        Map<String, SubjectCategory> byName = new LinkedHashMap<>();
        List<SubjectCategory> created = new ArrayList<>();

        for (CategorySeed seed : CATEGORIES) {
            Optional<SubjectCategory> existing = categoryDao.findByName(seed.name());
            if (existing.isPresent()) {
                byName.put(seed.name(), existing.get());
                continue;
            }

            SubjectCategory category = new SubjectCategory();
            category.setName(seed.name());
            category.setSortOrder(seed.sortOrder());
            category.setGradeFrom(seed.gradeFrom());
            category.setGradeTo(seed.gradeTo());
            category.setExpectedSubjects(seed.expected());
            category.setActive(Boolean.TRUE);
            created.add(category);
            byName.put(seed.name(), category);
        }

        if (!created.isEmpty()) {
            categoryDao.saveAll(created);
            log.info("Curriculum: created {} subject category row(s).", created.size());
        }
        return byName;
    }

    private Map<String, SubjectDetail> ensureSubjects() {
        Map<String, SubjectDetail> byName = new LinkedHashMap<>();
        for (SubjectDetail subject : subjectDao.findAll()) {
            byName.put(key(subject.getName()), subject);
        }

        // Rename first, so "Mathematics" is found rather than created alongside
        // the "Maths" row that already carries the timetable lines.
        List<SubjectDetail> renamed = new ArrayList<>();
        RENAMES.forEach((from, to) -> {
            SubjectDetail subject = byName.get(key(from));
            if (subject != null && !byName.containsKey(key(to))) {
                subject.setName(to);
                renamed.add(subject);
                byName.remove(key(from));
                byName.put(key(to), subject);
            }
        });
        if (!renamed.isEmpty()) {
            subjectDao.saveAll(renamed);
            log.info("Curriculum: renamed {} subject(s) to the school's spelling.", renamed.size());
        }

        List<SubjectDetail> created = new ArrayList<>();
        for (SubjectSeed seed : SUBJECTS) {
            if (byName.containsKey(key(seed.name()))) {
                continue;
            }
            SubjectDetail subject = new SubjectDetail();
            subject.setName(seed.name());
            subject.setCode(seed.code());
            subject.setActive(Boolean.TRUE);
            created.add(subject);
        }
        if (!created.isEmpty()) {
            subjectDao.saveAll(created);
            created.forEach(subject -> byName.put(key(subject.getName()), subject));
            log.info("Curriculum: created {} subject(s).", created.size());
        }

        return byName;
    }

    /** Points subjects at a category, without disturbing any already set. */
    private void classify(Map<String, SubjectDetail> subjects,
            Map<String, SubjectCategory> categories) {

        List<SubjectDetail> changed = new ArrayList<>();
        DEFAULT_CATEGORY.forEach((subjectName, categoryName) -> {
            SubjectDetail subject = subjects.get(key(subjectName));
            SubjectCategory category = categories.get(categoryName);
            if (subject == null || category == null) {
                return;
            }
            // Only fill a gap, or replace one of the groupings this class is
            // superseding; a category chosen by the school is left alone.
            String current = subject.getCategory() == null ? null : subject.getCategory().getName();
            if (current == null || SUPERSEDED.contains(current)) {
                subject.setCategory(category);
                changed.add(subject);
            }
        });

        if (!changed.isEmpty()) {
            subjectDao.saveAll(changed);
            log.info("Curriculum: classified {} subject(s) into the examination categories.",
                    changed.size());
        }
    }

    /** Deactivates the invented groupings, once no subject points at them. */
    private void retireSuperseded() {
        List<SubjectCategory> retired = new ArrayList<>();
        for (String name : SUPERSEDED) {
            categoryDao.findByName(name)
                    .filter(category -> Boolean.TRUE.equals(category.getActive()))
                    .filter(category -> categoryDao.countSubjects(category.getId()) == 0)
                    .ifPresent(category -> {
                        category.setActive(Boolean.FALSE);
                        retired.add(category);
                    });
        }
        if (!retired.isEmpty()) {
            categoryDao.saveAll(retired);
            log.info("Curriculum: retired {} superseded category row(s).", retired.size());
        }
    }

    /**
     * Fills in the subject list of every grade that has none.
     *
     * Per grade rather than all-or-nothing, so a school that has adjusted grade
     * 10 keeps its edit while a grade added later still gets seeded.
     */
    private void ensureCurriculum(Map<String, SubjectDetail> subjects) {
        Map<Integer, Grade> grades = new LinkedHashMap<>();
        for (Grade grade : gradeDao.findAll()) {
            Integer number = numberOf(grade.getName());
            if (number != null) {
                grades.put(number, grade);
            }
        }

        List<GradeSubject> rows = new ArrayList<>();
        List<String> seededGrades = new ArrayList<>();

        for (Map.Entry<Integer, Grade> entry : grades.entrySet()) {
            Grade grade = entry.getValue();
            if (!gradeSubjectDao.subjectIdsForGrade(grade.getId()).isEmpty()) {
                continue;
            }

            int order = 1;
            int before = rows.size();
            for (BasketSeed seed : CURRICULUM) {
                if (!seed.grades().contains(entry.getKey())) {
                    continue;
                }
                for (String subjectName : seed.subjects()) {
                    SubjectDetail subject = subjects.get(key(subjectName));
                    if (subject == null) {
                        log.warn("Curriculum: {} is on the grade {} curriculum but no such "
                                + "subject exists; skipped.", subjectName, entry.getKey());
                        continue;
                    }
                    GradeSubject row = new GradeSubject();
                    row.setGrade(grade);
                    row.setSubject(subject);
                    row.setBasket(seed.basket());
                    row.setSortOrder(order++);
                    row.setClassTeacherTaught(seed.classTeacher());
                    rows.add(row);
                }
            }
            if (rows.size() > before) {
                seededGrades.add(grade.getName());
            }
        }

        if (!rows.isEmpty()) {
            gradeSubjectDao.saveAll(rows);
            log.info("Curriculum: seeded {} subject placement(s) across {}.",
                    rows.size(), seededGrades);
        }
    }

    // ---- Helpers ------------------------------------------------------------

    private static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    /** "Grade 10" -> 10. Null for a grade named anything else. */
    private static Integer numberOf(String gradeName) {
        if (gradeName == null) {
            return null;
        }
        String digits = gradeName.replaceAll("\\D+", "");
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
