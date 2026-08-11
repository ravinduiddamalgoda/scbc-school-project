package com.scbck.model;


import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Status Entity
 * Represents the "status" table in database
 */
@Entity
@Table(name = "student_status")

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StudentStatus {

    /**
     * Primary key of the status table
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * Status name (e.g., Active, Inactive, Deleted)
     */
    private String name;
}