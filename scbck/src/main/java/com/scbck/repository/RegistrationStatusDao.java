package com.scbck.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.RegistrationStatus;

public interface RegistrationStatusDao extends JpaRepository<RegistrationStatus, Integer> {

    @Query("select s from RegistrationStatus s where lower(s.name) = lower(?1)")
    RegistrationStatus getByName(String name);
}
