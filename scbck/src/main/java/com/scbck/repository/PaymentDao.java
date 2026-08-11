package com.scbck.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.Payment;

public interface PaymentDao extends JpaRepository<Payment, Integer> {

    /**
     * One student's payment history, oldest first - the order the Fees Details
     * report reads, so the grades run 1, 2, 3 down the page.
     */
    @Query("""
            select p from Payment p
            where p.student_id.id = ?1
            order by p.paid_date, p.id
            """)
    List<Payment> listByStudent(Integer studentId);

    @Query("select p from Payment p order by p.paid_date desc, p.id desc")
    List<Payment> listNewestFirst();

    @Query("select p from Payment p where p.bill_no = ?1")
    Payment getByBillNo(String billNo);

    /**
     * Next receipt number as a plain number; the caller zero-pads it. CAST to
     * DECIMAL rather than MySQL's UNSIGNED so the statement also runs on H2.
     */
    @Query(value = """
            SELECT COALESCE(MAX(CAST(p.bill_no AS DECIMAL(18,0))), 0) + 1
            FROM payment p
            """, nativeQuery = true)
    long nextBillSequence();
}
