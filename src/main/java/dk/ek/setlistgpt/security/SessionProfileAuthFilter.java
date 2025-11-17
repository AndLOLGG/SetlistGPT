package dk.ek.setlistgpt.security;

import dk.ek.setlistgpt.profile.Profile;
import dk.ek.setlistgpt.profile.ProfileType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class SessionProfileAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            Profile p = sessionProfile(request);
            if (p != null) {
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
        // Allow all; adjust if you need skips.
        return false;
    }
}