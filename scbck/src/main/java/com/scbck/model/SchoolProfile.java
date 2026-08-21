package com.scbck.model;

/**
 * The school's own identifying facts.
 *
 * These are not records - there is exactly one school, and its census number
 * and Department of Examinations school number never change - but they are
 * printed on certificates, absence letters, mark sheets and every workbook the
 * school submits upwards. They used to be a {@code private static final String
 * SCHOOL} repeated in five services, each carrying a slightly different form of
 * the name, and none of them carrying the two numbers at all.
 *
 * Held together here so a heading printed by one export cannot drift from the
 * same heading printed by another.
 */
public final class SchoolProfile {

    /** Full name, as it heads a letter. */
    public static final String NAME = "Sri Chandananda Buddhist College";

    /** Name with its location, as it heads an export or signs a certificate. */
    public static final String NAME_WITH_CITY = NAME + ", Kandy";

    public static final String ADDRESS_LINE = "Asgiriya";

    public static final String CITY = "Kandy";

    /**
     * The census number the Ministry's annual school census identifies the
     * school by.
     */
    public static final String CENSUS_NO = "99059";

    /**
     * The Department of Examinations school number, printed in the "School No"
     * cell of every School Based Assessment and candidate workbook.
     */
    public static final String SCHOOL_ID = "12735";

    /** The examinations zone the school sits in. */
    public static final String ZONE = "Kandy";

    /** The three address lines, in the order a letterhead prints them. */
    public static final java.util.List<String> LETTERHEAD =
            java.util.List.of(NAME + ",", ADDRESS_LINE + ",", CITY);

    private SchoolProfile() {
    }
}
