package dk.ek.setlistgpt.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.web.csrf.CsrfToken;

import java.io.IOException;

/**
 * Ensures a CSRF token is generated so the XSRF-TOKEN cookie is sent on GET.
 */
public class WriteCsrfCookieFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token == null) {
            token = (CsrfToken) request.getAttribute("_csrf"); // fallback
        }
        if (token != null) {
            token.getToken(); // touch to trigger cookie write
        }
        chain.doFilter(request, response);
    }
}
