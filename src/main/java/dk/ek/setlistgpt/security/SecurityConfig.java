package dk.ek.setlistgpt.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.context.SecurityContextPersistenceFilter;

import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

import jakarta.servlet.http.HttpServletRequest;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repo.setCookiePath("/"); // ensure cookie path matches SPA usage
        return repo;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CookieCsrfTokenRepository csrfRepo) throws Exception {
        RequestMatcher loginPostMatcher = (HttpServletRequest req) ->
                "/api/login".equals(req.getServletPath()) && "POST".equalsIgnoreCase(req.getMethod());

        RequestMatcher logoutPostMatcher = (HttpServletRequest req) ->
                "/logout".equals(req.getServletPath()) && "POST".equalsIgnoreCase(req.getMethod());

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepo)
                        .ignoringRequestMatchers(loginPostMatcher, logoutPostMatcher)
                )
                .formLogin(form -> form.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/index", "/index.html",
                                "/profile", "/profile/**",
                                "/repertoires", "/repertoires/**",
                                "/setlists", "/setlists/**",
                                "/songs", "/songs/**",
                                "/javascript/**", "/styles.css", "/favicon.ico", "/error",
                                "/api/login",
                                "/api/csrf"
                        ).permitAll()
                        // Allow public POST to create profiles (signup)
                        .requestMatchers(HttpMethod.POST, "/api/profile/signup").permitAll()
                        // Allow public GET access to the repertoire listing/details used by the SPA.
                        .requestMatchers(HttpMethod.GET, "/api/repertoires", "/api/repertoires/**").permitAll()
                        // Protect all other API operations
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .permitAll()
                );

        // Ensure the session->SecurityContext filter runs AFTER persistence filter
        http.addFilterAfter(new SessionProfileAuthFilter(), SecurityContextPersistenceFilter.class);

        // Debug/inspection filter kept before CSRF filter
        http.addFilterBefore(new CsrfDebugFilter(csrfRepo), CsrfFilter.class);

        // Ensure CSRF cookie is written on GET responses
        http.addFilterAfter(new WriteCsrfCookieFilter(), CsrfFilter.class);

        return http.build();
    }
}