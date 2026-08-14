package com.scbck.model;

/**
 * The field rules the records share, in one place.
 *
 * The client already carries these in {@code validators.js} and its header
 * claims "the server enforces the same constraints independently". That was not
 * true: most text fields were annotated {@code @NotNull}, which accepts an
 * empty string, and none had a length or a format. Anything reaching the API
 * directly - a stale tab, a script, a mistyped fetch - could store a blank name
 * or a date of birth in 2090.
 *
 * The patterns are deliberately the same expressions the browser uses, so a
 * value that passes on screen cannot fail on save.
 */
public final class Validation {

    /** Letters, spaces, apostrophes, dots and hyphens - Sri Lankan full names. */
    public static final String PERSON_NAME = "^[A-Za-z][A-Za-z .'-]{1,99}$";

    /** Old 9-digit + V/X format, or the current 12-digit NIC. */
    public static final String NIC = "^([0-9]{9}[VvXx]|[0-9]{12})$";

    /** Local mobile and land numbers: ten digits beginning with zero. */
    public static final String PHONE = "^0[0-9]{9}$";

    /** Birth certificate references vary by district; this is the shared shape. */
    public static final String BIRTH_CERTIFICATE = "^[A-Za-z0-9/-]{6,12}$";

    /**
     * The earliest date of birth the system accepts.
     *
     * A guard against a mistyped year rather than a statement about age: 1900
     * is old enough for any staff record and clearly wrong for a typo like
     * 0209 or 1090.
     */
    public static final String EARLIEST_BIRTH_DATE = "1900-01-01";

    public static final int NAME_MAX = 100;
    public static final int ADDRESS_MAX = 255;
    public static final int SHORT_TEXT_MAX = 60;
    public static final int NOTE_MAX = 500;

    private Validation() {
    }
}
