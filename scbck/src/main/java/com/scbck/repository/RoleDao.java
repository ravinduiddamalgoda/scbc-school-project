package com.scbck.repository;


import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.Role;

// This interface defines a repository for the Role entity
// It extends JpaRepository to provide built-in CRUD operations for Role objects
public interface RoleDao extends JpaRepository<Role, Integer> {

    // Custom query to retrieve all roles except the one named 'Admin'
    @Query("select r from Role as r where r.name <> 'Admin'")
    List<Role> listWithoutAdmin();

    /**
     * Looked up by name rather than by id, because a database seeded in a
     * different order — or one where the role table was rebuilt — does not
     * necessarily give Admin id 1.
     */
    Optional<Role> findByName(String name);

    /*
     * JpaRepository provides default methods like:
     * - findAll(): Retrieve all Role records
     * - findById(Integer id): Retrieve a Role by its ID
     * - save(Role entity): Save or update a Role entity
     * - delete(Role entity): Delete a Role entity
     * - count(): Count total number of Role records
     */
}

