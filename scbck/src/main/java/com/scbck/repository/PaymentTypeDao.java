package com.scbck.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.PaymentType;

public interface PaymentTypeDao extends JpaRepository<PaymentType, Integer> {

    @Query("select t from PaymentType t where lower(t.name) = lower(?1)")
    PaymentType getByName(String name);
}
