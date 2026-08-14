package com.scbck.model;

import org.hibernate.validator.constraints.Length;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Guardian Entity - the parent or legal guardian a student is registered under.
 *
 * The "s_g_" prefixed fields describe the secondary guardian (the fallback
 * contact), mirroring the guardian table in the ER model.
 */
@Entity
@Table(name = "guardian")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Guardian {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Auto-generated, zero-padded guardian reference number. */
    @Column(name = "guardian_no", unique = true, length = 8)
    private String guardian_no;

    @NotBlank(message = "is required")
    @Size(max = Validation.NAME_MAX, message = "is too long")
    @Pattern(regexp = Validation.PERSON_NAME, message = "may only contain letters, spaces, . ' and -")
    private String fullname;

    @Column(name = "nic", unique = true)
    @Length(min = 10, max = 12, message = "must be 10 to 12 characters")
    @NotBlank(message = "is required")
    @Pattern(regexp = Validation.NIC, message = "must be 9 digits and V, or 12 digits")
    private String nic;

    @Length(max = 10, message = "must be at most 10 digits")
    @NotBlank(message = "is required")
    @Pattern(regexp = Validation.PHONE, message = "must be 10 digits starting with 0")
    private String mobile;

    @Column(name = "email")
    @Email(message = "must be a valid email address")
    private String email;

    private String occupation;

    private String employer;

    @NotBlank(message = "is required")
    @Size(max = Validation.ADDRESS_MAX, message = "is too long")
    private String address;

    /** Relationship of the primary guardian to the student. */
    @NotBlank(message = "is required")
    @Size(max = Validation.SHORT_TEXT_MAX, message = "is too long")
    private String relationship;

    // ---- Secondary guardian -------------------------------------------------

    private String s_g_name;

    @Length(max = 10, message = "must be at most 10 digits")
    @Pattern(regexp = Validation.PHONE, message = "must be 10 digits starting with 0")
    private String s_g_mobile;

    private String s_g_relationship;

    private String s_g_address;

    @Email(message = "must be a valid email address")
    private String s_g_email;
}
