package com.scbck.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository; // Import JpaRepository for database operations
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.Module;

/**
 * Repository interface for Module entity.
 * Extends JpaRepository to provide built-in CRUD operations.
 */
public interface ModuleDao extends JpaRepository<Module, Integer> {

    /*
     * Built-in JpaRepository methods:
     * - findAll(): Retrieve all Module records
     * - findById(id): Retrieve a Module by ID
     * - save(entity): Insert or update a Module
     * - delete(entity): Remove a Module
     * - count(): Count total Module records
     */

    /**
     * Retrieves modules that the given user DOES NOT have SELECT privilege for.
     *
     * Logic summary:
     * - Get all modules
     * - Exclude modules where:
     *   - User has roles
     *   - Roles have select privilege in privilage table
     *
     * Flow:
     * user → user_has_role → privilage → module
     */

    // Not schema-qualified, for the same reason as PrivilageDao: hard-coding
    // "scbc." pins the query to a database of that name and quietly reads the
    // wrong one everywhere else.
    @Query(
        value = """
            SELECT *
            FROM module AS m
            WHERE m.id NOT IN (
                SELECT p.module_id
                FROM privilage AS p
                WHERE p.privilage_select = true
                  AND p.role_id IN (
                      SELECT uhr.role_id
                      FROM user_has_role uhr
                      WHERE uhr.user_id IN (
                          SELECT u.id
                          FROM user AS u
                          WHERE u.username = ?1
                      )
                  )
            )
        """,
        nativeQuery = true
    )
    List<Module> getModulesByUser(String username);
}