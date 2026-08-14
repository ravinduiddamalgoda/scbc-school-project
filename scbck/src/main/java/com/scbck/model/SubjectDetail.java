package com.scbck.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A subject on the curriculum - "Buddhism", "Combined Maths", "Accounts".
 *
 * The ER model has the table (subject_detail) with only id and name. A subject
 * that is retired must stay in the database so historical reports keep their
 * meaning, which is what {@code active} is for; {@code code} gives the reports
 * a short column heading, and {@code category} groups the A/L optional baskets
 * the report spreadsheets label "Category 1/2/3".
 */
@Entity
@Table(name = "subject_detail")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SubjectDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", unique = true)
    @NotNull(message = "is required")
    private String name;

    /** Short form used as a column heading when the full name will not fit. */
    @Column(name = "code", length = 12)
    private String code;

    /**
     * The category band this subject prints in on the mark sheet.
     *
     * Optional: a subject with no category still works everywhere, it just
     * sorts after the categorised ones.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "subject_category_id", referencedColumnName = "id")
    private SubjectCategory category;

    /**
     * The free-text category this table used to carry.
     *
     * Kept mapped, and only ever read, so {@code SubjectCategoryBackfill} can
     * turn the values already in existing databases into real rows. Nothing
     * writes to it; once every deployment has started once on this version it
     * can be dropped.
     *
     * @deprecated superseded by {@link #category}.
     */
    @Deprecated
    @Column(name = "category", insertable = false, updatable = false)
    private String legacyCategory;

    /** Retired subjects stay readable in old reports but cannot be assigned. */
    @Column(name = "active")
    private Boolean active = Boolean.TRUE;
}
