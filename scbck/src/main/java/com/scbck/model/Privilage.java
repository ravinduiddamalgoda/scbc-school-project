package com.scbck.model;

// Import necessary Java and Jakarta persistence and validation libraries
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

// Define this class as a JPA entity mapped to the "privilage" table
@Entity
@Table(name = "privilage")
@Data // Lombok annotation to generate getters, setters, equals, hashCode, and toString methods
@AllArgsConstructor // Lombok annotation for generating a constructor with all arguments
@NoArgsConstructor // Lombok annotation for generating a no-argument constructor
public class Privilage {

    // Primary key with auto-increment strategy
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    // Boolean field to indicate select permission (cannot be null)
    @NotNull
    private Boolean privilage_select;

    // Boolean field to indicate insert permission (cannot be null)
    @NotNull
    private Boolean privilage_insert;

    // Boolean field to indicate update permission (cannot be null)
    @NotNull
    private Boolean privilage_update;

    // Boolean field to indicate delete permission (cannot be null)
    @NotNull
    private Boolean privilage_delete;

    // Many-to-One relationship with Role entity (Incorrect import, should use
    // proper Role entity)
    @ManyToOne
    @JoinColumn(name = "role_id", referencedColumnName = "id")
    private Role role_id; // Ensure this is a JPA entity

    // Many-to-One relationship with Module entity
    @ManyToOne
    @JoinColumn(name = "module_id", referencedColumnName = "id")
    private Module module_id;
}

