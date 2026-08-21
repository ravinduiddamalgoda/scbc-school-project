package com.scbck.model;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Something recorded about a student that is not a mark, a payment or a day of
 * attendance: a prefect appointment, a sport, a health observation, a note on
 * conduct.
 *
 * These are the four items on the Ministry's leaving form that the school had
 * nowhere to keep - conduct and behaviour, health conditions identified,
 * co-curricular activities and leadership, and other talents and abilities.
 * Until now the certificate form asked the office to type all four from memory
 * at the moment of issue, which meant a student's whole record of prefectship
 * and sport existed only inside certificates already handed out.
 *
 * Recorded here as they happen, over the student's years at the school, and
 * read back as the certificate's default text. The certificate still snapshots
 * what was printed - {@link StudentCertificate} explains why - so editing an
 * achievement afterwards does not rewrite a document already issued.
 */
@Entity
@Table(name = "student_achievement")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StudentAchievement {

    // ---- Kinds --------------------------------------------------------------

    /** A post held: class monitor, junior prefect, senior prefect. */
    public static final String LEADERSHIP = "LEADERSHIP";

    /** An activity outside the curriculum: a sport, art, music, dancing. */
    public static final String CO_CURRICULAR = "CO_CURRICULAR";

    /** Anything else the student is good at, in the school's own words. */
    public static final String TALENT = "TALENT";

    /** An observation on conduct and behaviour. */
    public static final String CONDUCT = "CONDUCT";

    /** A weakness or health condition identified at a medical examination. */
    public static final String HEALTH = "HEALTH";

    public static final List<String> KINDS =
            List.of(LEADERSHIP, CO_CURRICULAR, TALENT, CONDUCT, HEALTH);

    /**
     * The posts the school appoints to.
     *
     * A fixed list because it is fixed - these are the three posts that exist,
     * and "Other" carries anything the school invents, with {@link #otherType}
     * saying what it was. Held here rather than as a lookup table for the same
     * reason the media of instruction are: a fourth post would change the
     * leaving certificate's wording, which is a code change either way.
     */
    public static final List<String> LEADERSHIP_TYPES =
            List.of("Class Monitor", "Junior Prefect", "Senior Prefect", "Other");

    /** The co-curricular headings; "Sport" is the one with a second level. */
    public static final List<String> CO_CURRICULAR_TYPES =
            List.of("Art", "Music", "Dancing", "Drama", "Sport", "Other");

    /** The sports offered, chosen when the type is "Sport". */
    public static final List<String> SPORTS =
            List.of("Athletic", "Cricket", "Rugger", "Swimming", "Karate", "Badminton", "Chess");

    // ---- Columns ------------------------------------------------------------

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", referencedColumnName = "id")
    @NotNull(message = "is required")
    private Student student_id;

    /** One of {@link #KINDS}. */
    @NotNull(message = "is required")
    @Column(name = "kind", length = 20)
    private String kind;

    /**
     * The heading chosen from the dropdown - "Junior Prefect", "Sport", "Art".
     *
     * Null for conduct and health, which are a single free-text observation
     * with no type to pick.
     */
    @Column(name = "type", length = 60)
    @Size(max = 60, message = "is too long")
    private String type;

    /**
     * The second level, which today only sport has: "Cricket", "Swimming".
     */
    @Column(name = "sub_type", length = 60)
    @Size(max = 60, message = "is too long")
    private String subType;

    /** What "Other" meant, when the type is "Other". */
    @Column(name = "other_type", length = 120)
    @Size(max = 120, message = "is too long")
    private String otherType;

    /**
     * The line that prints on the certificate.
     *
     * For leadership this is the nature of the post and its year - "Junior
     * Prefect - 2021"; for a co-curricular activity it is the level reached and
     * what was won - "All Island Championship 2021". Free text because that is
     * what it is: the school writes an achievement the way it wants it read.
     */
    @Lob
    @Column(name = "detail")
    private String detail;

    /**
     * The year the achievement belongs to, when there is a single one.
     *
     * Kept apart from {@link #detail} so the list can be shown newest first,
     * which is the order a certificate reads best in. Optional - a health note
     * has no year.
     *
     * The column is {@code achievement_year} rather than {@code year} because
     * YEAR is a reserved word in both MySQL and H2: an unquoted one turns the
     * generated DDL into a syntax error, and the whole table quietly fails to
     * be created.
     */
    @Column(name = "achievement_year")
    private Integer year;

    private LocalDateTime added_datetime;

    private LocalDateTime updated_datetime;

    private Integer added_user_id;

    private Integer updated_user_id;

    /**
     * The achievement as one line of certificate text.
     *
     * The certificate draft joins these with newlines, which is why the
     * formatting lives on the entity rather than in the PDF service: the same
     * line is shown in the achievements list on screen, and the two must not
     * drift.
     */
    public String asLine() {
        String heading = headingText();
        if (detail == null || detail.isBlank()) {
            return heading;
        }
        if (heading.isBlank()) {
            return detail.trim();
        }
        return heading + " - " + detail.trim();
    }

    /** "Sport (Cricket)", "Junior Prefect", "Other (Debating)". */
    public String headingText() {
        if (type == null || type.isBlank()) {
            return "";
        }
        String head = "Other".equalsIgnoreCase(type) && otherType != null && !otherType.isBlank()
                ? otherType.trim()
                : type.trim();
        if (subType != null && !subType.isBlank()) {
            return head + " (" + subType.trim() + ")";
        }
        return head;
    }
}
