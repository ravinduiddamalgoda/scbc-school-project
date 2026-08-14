package com.scbck.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Something handed out to students and signed for - a uniform garment, a
 * textbook.
 *
 * The school's sheets hard-code their columns: the uniform sheet has six
 * headed JB(S), IB(S), SB(S), JB(TL), IB(TL), SB(TL), and the book sheet has
 * twelve numbered 1 to 12 with the titles written in by hand each year. Both
 * are the same shape - a roster with a column per thing issued - and both go
 * stale the moment the school changes what it gives out. Here the columns are
 * rows in a table, so a new garment or a different set of textbooks is a data
 * change rather than a new spreadsheet.
 */
@Entity
@Table(name = "distribution_item")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DistributionItem {

    /** Uniform garments - the columns of the uniform distribution sheet. */
    public static final String UNIFORM = "UNIFORM";

    /** Textbooks - the numbered columns of the book distribution sheet. */
    public static final String BOOK = "BOOK";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotNull(message = "is required")
    @Column(name = "name")
    private String name;

    /**
     * Short heading for the column, e.g. "JB(S)".
     *
     * The sheets are wide enough that a full name will not fit, which is why
     * the originals are headed with codes nobody outside the office can read.
     * The full name travels with it here so the legend is never lost.
     */
    @Column(name = "code", length = 16)
    private String code;

    /** {@link #UNIFORM} or {@link #BOOK}. */
    @NotNull(message = "is required")
    @Column(name = "kind", length = 16)
    private String kind;

    /**
     * The grade this item is for, or null when it goes to every grade.
     *
     * Textbooks are per grade; uniform sizes are not.
     */
    @Column(name = "grade_id")
    private Integer gradeId;

    /** Left-to-right column order on the sheet. */
    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    /** Retired items stay on past sheets but cannot be issued. */
    @Column(name = "active")
    private Boolean active = Boolean.TRUE;
}
