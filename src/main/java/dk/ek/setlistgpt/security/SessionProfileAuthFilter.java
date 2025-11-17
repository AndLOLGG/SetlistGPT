package dk.ek.setlistgpt.security;

import dk.ek.setlistgpt.profile.Profile;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Restores authentication from the HTTP session attribute "profile".
 * Replaces anonymous or stale authentication so security checks and CSRF
 * use the restored principal.
 */
public class SessionProfileAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        Profile p = sessionProfile(request);

        if (p != null) {
            boolean shouldSet = false;

            if (existing == null) {
                shouldSet = true;
            } else if (existing instanceof AnonymousAuthenticationToken) {
                shouldSet = true;
            } else {
                // If existing principal is not the same profile instance/id, replace it.
                Object principal = existing.getPrincipal();
                if (!(principal instanceof Profile)) {
                    shouldSet = true;
                } else {
                    Profile current = (Profile) principal;
                    // compare by id if available, fallback to name
                    if (current.getId() == null || p.getId() == null) {
                        if (!Objects.equals(current.getName(), p.getName())) shouldSet = true;
                    } else if (!Objects.equals(current.getId(), p.getId())) {
                        shouldSet = true;
                    }
                }
            }

            if (shouldSet) {
                String role = "ROLE_" + p.getType().name();
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(p, null,
                                List.of(new SimpleGrantedAuthority(role)));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        chain.doFilter(request, response);
    }

    private Profile sessionProfile(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) return null;
        Object obj = session.getAttribute("profile");
        return (obj instanceof Profile) ? (Profile) obj : null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return false;
    }
}