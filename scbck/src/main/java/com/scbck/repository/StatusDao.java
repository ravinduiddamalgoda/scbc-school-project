package com.scbck.repository;

// Importing JpaRepository to provide built-in JPA CRUD operations
import org.springframework.data.jpa.repository.JpaRepository;

import com.scbck.model.Status;

/**
 * Repository interface for Status entity.
 * 
 * Extends JpaRepository to provide ready-made database operations
 * such as save, findById, findAll, delete, etc.
 * 
 * Generic parameters:
 * Status  -> Entity type managed by this repository
 * Integer -> Type of the primary key of Status entity
 */
public interface StatusDao extends JpaRepository<Status, Integer> {

    // No methods are required here because JpaRepository
    // already provides full CRUD and pagination support.

}