package com.scbck.model;


import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data; 
import lombok.NoArgsConstructor; 

@Entity // Marks this class as a JPA entity, making it a table in the database
@Table(name = "module") // Maps this entity to the "role" table in the database

@Data // Lombok annotation to generate getter, setter, toString, equals, and hashCode methods
@AllArgsConstructor // Lombok annotation to generate a constructor with all fields as arguments
@NoArgsConstructor // Lombok annotation to generate a default constructor with no arguments


public class Module {
 @Id // Marks this field as the primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Enables auto-increment for the primary key
    private Integer id; // Represents the primary key for the role table

    // Column mapping (Optional, only needed if the column name differs from the field name)
    private String name; // Represents the role name in the table
}
