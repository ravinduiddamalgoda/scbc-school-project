package com.scbck.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.Religion;

public interface ReligionDao extends JpaRepository<Religion, Integer> {

    /** Matched case-insensitively, because the register's spelling varies. */
    @Query("select r from Religion r where lower(r.name) = lower(?1)")
    Religion getByName(String name);

    @Query("select r from Religion r order by r.name")
    List<Religion> listOrdered();
}
