package com.scbck.security;

import java.io.IOException;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Forces the deferred CsrfToken to be resolved on every request.
 *
 * CookieCsrfTokenRepository only writes the XSRF-TOKEN cookie once the token
 * value is actually read. Without this filter the SPA would have no token
 * available for its very first POST (the login call), because nothing had
 * touched the token yet.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            // Reading the value triggers the repository to render the cookie.
            csrfToken.getToken();
        }

        filterChain.doFilter(request, response);
    }
}
