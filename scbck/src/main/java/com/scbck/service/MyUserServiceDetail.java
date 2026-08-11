package com.scbck.service;

import java.util.HashSet;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scbck.model.Role;
import com.scbck.model.User;
import com.scbck.repository.UserDao;

/**
 * Loads an account and its granted authorities for Spring Security.
 */
@Service
public class MyUserServiceDetail implements UserDetailsService {

    private final UserDao userDao;

    public MyUserServiceDetail(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        User user = userDao.getByUsername(username);

        // The previous version dereferenced the result unconditionally, so an
        // unknown username surfaced as a NullPointerException instead of a
        // failed authentication.
        if (user == null) {
            throw new UsernameNotFoundException("No account found for " + username);
        }

        Set<GrantedAuthority> authorities = new HashSet<>();
        if (user.getRoles() != null) {
            for (Role role : user.getRoles()) {
                authorities.add(new SimpleGrantedAuthority(role.getName()));
            }
        }

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                Boolean.TRUE.equals(user.getStatus()), // enabled
                true, // account non-expired
                true, // credentials non-expired
                true, // account non-locked
                authorities);
    }
}
