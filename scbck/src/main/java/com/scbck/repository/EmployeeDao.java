package com.scbck.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.Employee;

/**
 * Repository interface for Employee entity.
 * 
 * Extends JpaRepository to provide built-in CRUD operations,
 * pagination, and sorting functionality without writing implementation code.
 * 
 * Generic parameters:
 * Employee -> Entity type this repository manages
 * Integer  -> Type of the primary key of Employee entity
 */
public interface EmployeeDao extends JpaRepository<Employee, Integer> {
    
    // No need to write any methods here because JpaRepository
    // already provides all basic CRUD operations automatically.

    // Retrieves the next available employee number as an 8-digit string, padding it
     // with leading zeros.
     // Uses a native SQL query to fetch the highest employee number, increment it by
     // 1, and format it.
    //  @Query(value = "SELECT LPAD(MAX(e.emp_no) + 1, 8, '0') FROM scbc.employee AS e;", nativeQuery = true)
    //  String getNextEmpNo();


    // "FROM employee", not "FROM scbc.employee": the qualified form read the
    // highest staff number out of a database called scbc whatever database the
    // application was actually connected to, so a second deployment would hand
    // out numbers colliding with rows it could not see.
    @Query(value = """
SELECT LPAD(
    COALESCE(MAX(CAST(e.emp_no AS UNSIGNED)), 0) + 1,
    8,
    '0'
)
FROM employee e
""", nativeQuery = true)
String getNextEmpNo();

     /*
      * ----------------------------
      * 2 JPA Query (JPQL - Java Persistence Query Language)
      * ----------------------------
      * - JPQL is an object-oriented query language provided by JPA.
      * - It operates on entity objects rather than database tables.
      * - The advantage of JPQL is that it works across different database types.
      * - Unlike native queries, JPQL does NOT require "nativeQuery = true".
      */

     // Retrieves an Employee entity by NIC (National Identity Card Number)
     // JPQL uses "Employee" (the entity name) instead of the table name.
     @Query(value = "SELECT e FROM Employee e WHERE e.nic = ?1")
     Employee getByNIC(String nic);

     // Retrieves an Employee entity by email
     @Query(value = "SELECT e FROM Employee e WHERE e.email = ?1")
     Employee getByEmail(String email);

     // Retrieves an Employee entity by mobile number
     @Query(value = "SELECT e FROM Employee e WHERE e.mobileno = ?1")
     Employee getByMobile(String mobileno);

     // Custom query to find all Employees who do not have an associated User account
     @Query(value = "SELECT e FROM Employee AS e WHERE e.id NOT IN (SELECT u.employee_id.id FROM User AS u WHERE u.employee_id IS NOT NULL)")
     // Method to return a list of employees without user accounts
     List<Employee> listUsersWithoutAccount();
    
}