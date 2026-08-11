package com.scbck.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.scbck.security.CsrfCookieFilter;
import com.scbck.security.RestAccessDeniedHandler;

/**
 * Security configuration for the REST (client-server) architecture.
 *
 * The application no longer renders server-side pages, so this chain never
 * issues redirects: unauthenticated calls return 401 and unauthorised calls
 * return 403 with a JSON body that the React client can act on.
 *
 * Authentication state is kept in an HttpOnly session cookie (JSESSIONID),
 * which keeps credentials out of reach of JavaScript. CSRF protection is
 * therefore enabled and uses the double-submit cookie pattern so the SPA can
 * echo the token back in the X-XSRF-TOKEN header.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Origins allowed to call the API when the client is served from a different
     * host than the backend (production split-domain deployments). During local
     * development the Vite dev server proxies /api, so requests are same-origin
     * and CORS is not exercised.
     */
    @Value("${scbc.cors.allowed-origins:http://localhost:5173,http://localhost:4173}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            RestAccessDeniedHandler accessDeniedHandler) throws Exception {

        // Spring Security 6 stores the token in a request attribute by default as a
        // defence against BREACH. A SPA reads the token from the cookie instead, so
        // the plain attribute handler is used here.
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName(null);

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfRequestHandler))

                // Render the XSRF-TOKEN cookie on every response so the client always has a
                // usable token before it issues its first mutating request.
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)

                // A session is created on login and reused; it is never created eagerly.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.migrateSession()))

                .authorizeHttpRequests(auth -> auth
                        // Authentication endpoints must be reachable while logged out.
                        .requestMatchers("/api/auth/login", "/api/auth/csrf").permitAll()

                        // One-time bootstrap of the initial Admin account.
                        .requestMatchers("/api/auth/createadmin").permitAll()

                        // CORS pre-flight.
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()

                        // Everything else requires a session. Fine-grained authorisation is
                        // enforced per module by PrivilegeService inside each controller.
                        .anyRequest().authenticated())

                // No login page, no redirects: the client owns navigation.
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())

                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> response
                                .setStatus(HttpStatus.NO_CONTENT.value())))

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(accessDeniedHandler));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Patterns rather than exact origins. An entry without a wildcard still
        // matches exactly, so the configured default behaves as before; the
        // difference is that a deployment may now set something like
        // SCBC_CORS_ORIGINS=http://localhost:[*] without a code change.
        // setAllowedOrigins("*") would not work here at all - it is rejected in
        // combination with allowCredentials, which the session cookie needs.
        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // Required for the session and CSRF cookies to travel cross-origin.
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    /**
     * Exposed so AuthController can authenticate a JSON login payload manually.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
