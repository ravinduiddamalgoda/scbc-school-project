package com.scbck.model;

import java.time.LocalDateTime;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.scbck.json.DataUrlDeserializer;
import com.scbck.json.DataUrlSerializer;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank(message = "is required")
    @Size(min = 3, max = 45, message = "must be 3 to 45 characters")
    @Column(name = "username", unique = true)
    private String username;

    /**
     * BCrypt hash.
     *
     * WRITE_ONLY keeps the hash out of every response body. It used to be
     * serialised with the rest of the entity, which meant any account holding
     * select rights on the User module could harvest every password hash.
     */
    @NotNull(message = "is required")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @NotBlank(message = "is required")
    @Email(message = "must be a valid email address")
    @Column(name = "useremail", unique = true)
    private String useremail;

    @NotNull(message = "is required")
    private Boolean status;

    private LocalDateTime added_datetime;

    private LocalDateTime updatedatetime;

    private LocalDateTime deleted_datetime;

    private String note;

    @Lob
    @Column(name = "userphoto", columnDefinition = "LONGBLOB")
    @JsonSerialize(using = DataUrlSerializer.class)
    @JsonDeserialize(using = DataUrlDeserializer.class)
    private byte[] userphoto;

    @ManyToOne(optional = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id", referencedColumnName = "id")
    private Employee employee_id;

    /**
     * The guardian this account belongs to, for a parent login.
     *
     * A parent account is not staff with fewer rights: it is an account that
     * may only ever see the children linked to one guardian record, and the
     * link is what says which. Staff accounts have {@code employee_id} and no
     * guardian; parent accounts have the reverse.
     *
     * Set by the office rather than by self-registration, so the school decides
     * who is given sight of a child's marks - which is the whole point of
     * hanging it off the guardian record that is already on file.
     */
    @ManyToOne(optional = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "guardian_id", referencedColumnName = "id")
    private Guardian guardian_id;

    @ManyToMany(cascade = CascadeType.MERGE, fetch = FetchType.EAGER)
    @JoinTable(name = "user_has_role", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles;
}
