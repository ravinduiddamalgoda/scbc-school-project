package com.scbck.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.scbck.model.User;

public interface UserDao extends JpaRepository<User, Integer> {

    @Query("select u from User u where u.username = ?1")
    User getByUsername(String username);

    @Query("select u from User u where u.useremail = ?1")
    User getByUseremail(String useremail);

    /**
     * Every account except the caller's own and the built-in Admin, so the User
     * screen cannot be used to tamper with either.
     */
    @Query("select u from User u where u.username <> ?1 and u.username <> 'Admin' order by u.id desc")
    List<User> findAllExceptUsername(String username);
}
