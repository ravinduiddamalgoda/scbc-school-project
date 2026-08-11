package com.scbck.repository;

// Importing JpaRepository to provide standard JPA database operations
import org.springframework.data.jpa.repository.JpaRepository;

import com.scbck.model.Designation;

/**
 * Repository interface for Designation entity.
 * 
 * Extends JpaRepository to provide built-in CRUD operations,
 * pagination, and sorting support without requiring implementation code.
 * 
 * Generic parameters:
 * Designation -> Entity type managed by this repository
 * Integer     -> Type of the primary key of Designation entity
 */
public interface DesignationDao extends JpaRepository<Designation, Integer> {

    // No custom methods defined because JpaRepository already provides
    // all basic database operations automatically.

}