package dk.ek.setlistgpt.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
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
        // RequestMatcher to exempt the JSON login POST from CSRF checks
        RequestMatcher loginPostMatcher = (HttpServletRequest req) ->
                "/api/login".equals(req.getServletPath()) && "POST".equalsIgnoreCase(req.getMethod());

        // Temporary: ignore POST /api/repertoires to unblock while debugging (remove ASAP)
        RequestMatcher repertoiresPostMatcher = (HttpServletRequest req) ->
                "/api/repertoires".equals(req.getServletPath()) && "POST".equalsIgnoreCase(req.getMethod());

        // Exempt logout POST from CSRF checks to avoid 403 during logout while debugging.
        // Remove this exemption in production and fix the underlying CSRF/session mismatch instead.
        RequestMatcher logoutPostMatcher = (HttpServletRequest req) ->
                "/logout".equals(req.getServletPath()) && "POST".equalsIgnoreCase(req.getMethod());

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepo)
                        .ignoringRequestMatchers(loginPostMatcher, repertoiresPostMatcher, logoutPostMatcher)
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
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .permitAll()
                );

        // add debug filter before CSRF filter so it can inspect cookie/header before validation
        http.addFilterBefore(new CsrfDebugFilter(csrfRepo), CsrfFilter.class);

        // keep the cookie write helper
        http.addFilterAfter(new WriteCsrfCookieFilter(), CsrfFilter.class);

        return http.build();
    }
}
