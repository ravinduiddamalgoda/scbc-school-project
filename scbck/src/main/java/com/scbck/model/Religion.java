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
 * A religion and the Department of Examinations code for it.
 *
 * {@code student.religion} is free text and stays that way - it is what the
 * admission register holds, and rewriting every student row to an id would
 * break the records the school already has. This table maps that text to the
 * number the candidate workbooks require (11-16), matched by name.
 *
 * A religion with no row here simply has no code, and the export says which
 * students are affected rather than quietly writing a blank into a form the
 * Department will reject.
 */
@Entity
@Table(name = "religion")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Religion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotNull(message = "is required")
    @Column(name = "name", unique = true, length = 60)
    private String name;

    /** Department of Examinations code, 11-16. */
    @Column(name = "exam_code")
    private Integer examCode;
}
