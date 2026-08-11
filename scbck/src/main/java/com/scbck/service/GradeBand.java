package com.scbck.service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.scbck.model.Grade;

/**
 * The four grade bands the report spreadsheets are laid out in.
 *
 * The originals put the bands side by side across one sheet, which is why the
 * "Subject wise Student Count" workbook runs out to column BO. Printed, that
 * only works because each band carries just its own subjects - a primary class
 * has no Combined Maths column. Keeping the bands is therefore not decoration:
 * it is what makes the wide reports fit on a page at a readable size.
 */
public record GradeBand(String title, int from, int to) {

    /** Digits anywhere in the grade name: "Grade 10" -> 10. */
    private static final Pattern LEVEL = Pattern.compile("(\\d+)");

    public static final GradeBand OTHER = new GradeBand("Other grades", Integer.MIN_VALUE, Integer.MAX_VALUE);

    public static final List<GradeBand> ALL = List.of(
            new GradeBand("Grade 1 to Grade 5", 1, 5),
            new GradeBand("Grade 6 to Grade 9", 6, 9),
            new GradeBand("Grade 10 to Grade 11", 10, 11),
            new GradeBand("Grade 12 to Grade 13", 12, 13));

    /**
     * The numeric level of a grade, or null when its name carries no number.
     *
     * Parsing the name rather than storing a level keeps this working against
     * the grade rows already seeded in every existing database, which have no
     * such column.
     */
    public static Integer levelOf(Grade grade) {
        if (grade == null || grade.getName() == null) {
            return null;
        }
        Matcher matcher = LEVEL.matcher(grade.getName());
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    /** The band a grade belongs to; unnumbered grades fall into "Other". */
    public static GradeBand of(Grade grade) {
        Integer level = levelOf(grade);
        if (level == null) {
            return OTHER;
        }
        return ALL.stream()
                .filter(band -> band.contains(level))
                .findFirst()
                .orElse(OTHER);
    }

    public boolean contains(int level) {
        return level >= from && level <= to;
    }
}
