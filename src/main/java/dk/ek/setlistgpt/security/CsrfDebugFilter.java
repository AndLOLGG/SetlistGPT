package dk.ek.setlistgpt.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

/**
 * Debug filter: logs incoming CSRF cookie/header/session id and token loaded by the shared repository.
 * Deploy temporarily to inspect why CSRF validation fails.
 */
public class CsrfDebugFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CsrfDebugFilter.class);
    private final CookieCsrfTokenRepository csrfRepo;

    public CsrfDebugFilter(CookieCsrfTokenRepository csrfRepo) {
        this.csrfRepo = csrfRepo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String sessionId = Optional.ofNullable(request.getSession(false)).map(s -> s.getId()).orElse("<no-session>");
            String cookieVal = "<none>";
            if (request.getCookies() != null) {
                cookieVal = Arrays.stream(request.getCookies())
                        .filter(c -> "XSRF-TOKEN".equals(c.getName()))
                        .map(Cookie::getValue)
                        .findFirst()
                        .orElse("<none>");
            }
            String headerVal = Optional.ofNullable(request.getHeader("X-XSRF-TOKEN")).orElse("<none>");
            CsrfToken repoToken = csrfRepo.loadToken(request);
            String repoTokenVal = repoToken != null ? repoToken.getToken() : "<repo-none>";
            log.info("CSRF DEBUG: method={} path={} session={} cookie={} header={} repoToken={}",
                    request.getMethod(), request.getRequestURI(), sessionId, cookieVal, headerVal, repoTokenVal);
        } catch (Exception ex) {
            log.warn("CSRF DEBUG: failed to inspect token", ex);
        }
        chain.doFilter(request, response);
    }
}
