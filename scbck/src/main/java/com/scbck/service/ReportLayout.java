package com.scbck.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.scbck.model.AcademicYear;
import com.scbck.model.Classroom;
import com.scbck.model.SubjectDetail;

import com.scbck.dto.ReportDocument;
import com.scbck.dto.ReportSection;

/**
 * The layout decisions every report shares: what order classes print in, which
 * band a grade belongs to, how a document is assembled.
 *
 * Kept in one place because a report that ordered its grades differently from
 * the one beside it would look like a bug in the data rather than in the
 * printing.
 */
public final class ReportLayout {

    public static final String PORTRAIT = "portrait";
    public static final String LANDSCAPE = "landscape";

    private ReportLayout() {
    }

    public static ReportDocument document(String key, String title, String description,
            AcademicYear year, String orientation, List<ReportSection> sections) {
        return new ReportDocument(key, title, description, year.getName(),
                LocalDateTime.now(), orientation, sections);
    }

    /** Classes grouped into bands, each band ordered by grade then class name. */
    public static Map<GradeBand, List<Classroom>> groupByBand(List<Classroom> classrooms) {
        Map<GradeBand, List<Classroom>> grouped = new LinkedHashMap<>();

        List<Classroom> ordered = new ArrayList<>(classrooms);
        ordered.sort(classroomOrder());

        for (Classroom classroom : ordered) {
            grouped.computeIfAbsent(GradeBand.of(classroom.getGrade_id()), key -> new ArrayList<>()).add(classroom);
        }

        // Bands print in curriculum order regardless of the order rows arrived in.
        Map<GradeBand, List<Classroom>> sorted = new LinkedHashMap<>();
        for (GradeBand band : orderedBands(grouped.keySet())) {
            sorted.put(band, grouped.get(band));
        }
        return sorted;
    }

    public static List<GradeBand> orderedBands(Set<GradeBand> present) {
        List<GradeBand> ordered = new ArrayList<>(GradeBand.ALL.stream().filter(present::contains).toList());
        if (present.contains(GradeBand.OTHER)) {
            ordered.add(GradeBand.OTHER);
        }
        return ordered;
    }

    /**
     * Core subjects first, then the optional baskets, alphabetical within each -
     * so a band's columns read the way the timetable does instead of jumping
     * between Art and Accounts.
     *
     * The order comes from the category's own {@code sortOrder} rather than its
     * name, which is what lets the school decide that "Category 2" prints after
     * the compulsory subjects without having to name the bands alphabetically.
     * Uncategorised subjects sort last.
     */
    public static List<SubjectDetail> orderedSubjects(Iterable<SubjectDetail> subjects) {
        List<SubjectDetail> ordered = new ArrayList<>();
        subjects.forEach(ordered::add);
        ordered.sort(subjectOrder());
        return ordered;
    }

    /**
     * The shared column order: category band, then the subject's own position
     * within it, then name.
     *
     * Name alone was not enough. The school's sheets run in curriculum order -
     * Sinhala before Buddhism before Mathematics - which no alphabetical rule
     * reproduces, so a subject carries its own position and name is only the
     * tie-break for the ones that have none.
     */
    public static Comparator<SubjectDetail> subjectOrder() {
        return Comparator
                .comparingInt(ReportLayout::categoryOrder)
                .thenComparing(ReportLayout::categoryName, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(ReportLayout::subjectPosition)
                .thenComparing(SubjectDetail::getName, String.CASE_INSENSITIVE_ORDER);
    }

    /** A subject's position in its band; unplaced subjects sort after placed ones. */
    public static int subjectPosition(SubjectDetail subject) {
        return subject.getSortOrder() == null ? Integer.MAX_VALUE : subject.getSortOrder();
    }

    /** Position of a subject's category band, with uncategorised ones last. */
    public static int categoryOrder(SubjectDetail subject) {
        if (subject.getCategory() == null || subject.getCategory().getSortOrder() == null) {
            return Integer.MAX_VALUE;
        }
        return subject.getCategory().getSortOrder();
    }

    public static String categoryName(SubjectDetail subject) {
        return subject.getCategory() == null ? "" : subject.getCategory().getName();
    }

    public static Comparator<Classroom> classroomOrder() {
        return Comparator
                .comparingInt(ReportLayout::levelOrder)
                .thenComparing(Classroom::getName, String.CASE_INSENSITIVE_ORDER);
    }

    /** Numeric grade level, with unnumbered grades sorted to the end. */
    public static int levelOrder(Classroom classroom) {
        Integer level = GradeBand.levelOf(classroom.getGrade_id());
        return level == null ? Integer.MAX_VALUE : level;
    }

    public static String gradeName(Classroom classroom) {
        return classroom.getGrade_id() == null ? "—" : classroom.getGrade_id().getName();
    }

    public static String classLabel(Classroom classroom) {
        return gradeName(classroom) + " " + classroom.getName();
    }

    /** The short code when the subject has one; the full name otherwise. */
    public static String heading(SubjectDetail subject) {
        return subject.getCode() == null || subject.getCode().isBlank() ? subject.getName() : subject.getCode();
    }

    /**
     * A percentage as the reports print it.
     *
     * Zero days conducted yields a dash rather than 0% - "no school was held"
     * and "nobody turned up" are different facts, and the source workbook's
     * divide-by-zero showed the difference as #DIV/0!.
     */
    public static String percentage(long attended, long conducted) {
        if (conducted <= 0) {
            return "—";
        }
        return Math.round(attended * 1000.0 / conducted) / 10.0 + "%";
    }
}
