package com.scbck.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.Guardian;

public interface GuardianDao extends JpaRepository<Guardian, Integer> {

    @Query("select g from Guardian g where g.nic = ?1")
    Guardian getByNic(String nic);

    @Query("select g from Guardian g where g.mobile = ?1")
    Guardian getByMobile(String mobile);

    @Query("select g from Guardian g where g.email = ?1")
    Guardian getByEmail(String email);

    /**
     * Next zero-padded guardian reference. COALESCE keeps the first insert
     * working on an empty table.
     */
    @Query(value = """
            SELECT LPAD(COALESCE(MAX(CAST(g.guardian_no AS UNSIGNED)), 0) + 1, 8, '0')
            FROM guardian g
            """, nativeQuery = true)
    String getNextGuardianNo();
}
