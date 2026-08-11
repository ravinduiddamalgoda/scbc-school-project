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
 * Designation Entity
 * Represents the "designation" table in the database
 */
@Entity
@Table(name = "designation")

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Designation {

    /**
     * Primary key of designation table
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * Name of designation (e.g., Manager, Teacher, Clerk)
     */
    private String name;
    private Integer role_id;
    private Boolean user_account;

}