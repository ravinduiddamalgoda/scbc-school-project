package com.scbck.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.Privilage;

// The repository interface for Privilage entity, extending JpaRepository for CRUD operations
public interface PrivilageDao extends JpaRepository<Privilage, Integer> {

    // Custom JPQL query method to fetch a Privilage entity
    // Fetches a single privilege where the role ID and module ID match the given
    // parameters
    @Query(value = "select p from Privilage p where p.role_id.id = ?1 and p.module_id.id = ?2")
    Privilage getPrivilageRoleModule(Integer roleid, Integer moduleid2);

    // Native SQL query to fetch user privileges for a specific module
    // BIT_OR is used to aggregate privileges across all roles the user has for the
    // module
    // Returns select, insert, update, and delete privileges as a combined result
    // 1. Filters modules by module name
    // 2. Filters user roles by username
    // 3. Joins privilege table with roles and modules to compute effective
    // privileges
    // The table names are deliberately NOT schema-qualified. They used to read
    // "scbc.privilage", which pinned the query to a database of that exact
    // name: point the application at any other one - a staging copy, a second
    // school, a database restored under a different name - and this silently
    // returned no privileges from the wrong schema, so every user appeared to
    // have no access at all rather than anything failing.
    @Query(value = """
            SELECT BIT_OR(p.privilage_select), BIT_OR(p.privilage_insert),
                   BIT_OR(p.privilage_update), BIT_OR(p.privilage_delete)
            FROM privilage AS p
            WHERE p.module_id IN (SELECT m.id FROM module AS m WHERE m.name = ?2)
              AND p.role_id IN (
                  SELECT uhr.role_id FROM user_has_role AS uhr
                  WHERE uhr.user_id IN (SELECT u.id FROM user AS u WHERE u.username = ?1))
            """, nativeQuery = true)
    String getUserPrivilageByUserModule(String username, String modulename);
}