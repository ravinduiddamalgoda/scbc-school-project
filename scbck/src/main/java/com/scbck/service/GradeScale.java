package com.scbck.service;

import java.util.List;

/**
 * The letter grade a mark earns, and the mark bands the mark sheet counts in.
 *
 * Transcribed from the formula the school's own workbook carries in every grade
 * cell:
 *
 * <pre>
 * =IF(E9="AB","AB",IF(E9&gt;=75,"A",IF(E9&gt;=65,"B",IF(E9&gt;=55,"C",IF(E9&gt;=35,"S","F")))))
 * </pre>
 *
 * Kept as one constant rather than spread across the exporters, because the
 * workbook proves how that ends: its own summary block counts the bands
 * 0-34 / 35-54 / 55-64 / 65-74 / 75-100, which is the same scale written a
 * second time, and the two would have to be edited together forever.
 */
public final class GradeScale {

    /** Shown for a subject the student does not take. */
    public static final String NOT_TAKEN = "-";

    /** Shown for a subject the student was absent from. */
    public static final String ABSENT = "AB";

    /** The letters a mark can earn, best first. Drives the summary columns. */
    public static final List<String> LETTERS = List.of("A", "B", "C", "S", "F");

    /**
     * The mark ranges the summary block counts, worst first - the order the
     * workbook prints them in.
     */
    public static final List<Band> BANDS = List.of(
            new Band("0-34", 0, 34),
            new Band("35-54", 35, 54),
            new Band("55-64", 55, 64),
            new Band("65-74", 65, 74),
            new Band("75-100", 75, 100));

    private GradeScale() {
    }

    /**
     * The letter for a mark.
     *
     * @param marks  the mark, or null when none was recorded
     * @param absent whether the student was marked absent
     */
    public static String letterFor(Integer marks, boolean absent) {
        if (absent) {
            return ABSENT;
        }
        if (marks == null) {
            return NOT_TAKEN;
        }
        if (marks >= 75) {
            return "A";
        }
        if (marks >= 65) {
            return "B";
        }
        if (marks >= 55) {
            return "C";
        }
        if (marks >= 35) {
            return "S";
        }
        return "F";
    }

    /** The band label a mark falls in, or null when there is no mark. */
    public static String bandFor(Integer marks) {
        if (marks == null) {
            return null;
        }
        return BANDS.stream()
                .filter(band -> band.contains(marks))
                .map(Band::label)
                .findFirst()
                .orElse(null);
    }

    public record Band(String label, int from, int to) {
        public boolean contains(int marks) {
            return marks >= from && marks <= to;
        }
    }
}
