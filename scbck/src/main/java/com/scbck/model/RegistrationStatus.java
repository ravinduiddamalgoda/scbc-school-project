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
 * State of a class enrolment: Active, Transferred, Cancelled.
 *
 * Only "Active" enrolments are counted by the student-count reports, so a
 * student who left mid-year stops inflating their old class without the
 * enrolment record being destroyed.
 */
@Entity
@Table(name = "registration_status")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegistrationStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;
}
