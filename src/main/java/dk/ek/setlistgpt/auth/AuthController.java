package dk.ek.setlistgpt.auth;

import dk.ek.setlistgpt.profile.Profile;
import dk.ek.setlistgpt.profile.ProfileRepository;
import dk.ek.setlistgpt.profile.ProfileType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple session login: finds a profile by name and (demo) password check.
 * Replace password handling with proper hashing in production.
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final ProfileRepository profiles;
    private final CookieCsrfTokenRepository csrfRepo;

    public AuthController(ProfileRepository profiles, CookieCsrfTokenRepository csrfRepo) {
        this.profiles = profiles;
        this.csrfRepo = csrfRepo;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest body,
                                   HttpServletRequest request,
                                   HttpServletResponse response) {
        if (body == null || body.getName() == null || body.getPassword() == null) {
            log.debug("Login attempt with missing credentials: bodyPresent={}, namePresent={}, passwordPresent={}",
                    body != null, body != null && body.getName() != null, body != null && body.getPassword() != null);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Missing credentials"));
        }

        String name = body.getName().trim();
        String password = body.getPassword();

        log.debug("Login attempt for name='{}' (password provided={})", name, password != null && !password.isEmpty());

        boolean isAdminCreds = "admin".equals(name) && "admin".equals(password);
        Profile p = profiles.findByName(name).orElse(null);

        if (p == null) {
            ProfileType type = isAdminCreds ? ProfileType.ADMIN : ProfileType.MUSICIAN;
            p = new Profile();
            p.setName(name);
            p.setType(type);
            p = profiles.save(p);
            log.debug("Created new profile: name='{}' type={}", name, type);
        } else {
            log.debug("Found existing profile: id={} name='{}' type={}", p.getId(), p.getName(), p.getType());
            if (isAdminCreds && p.getType() != ProfileType.ADMIN) {
                p.setType(ProfileType.ADMIN);
                p = profiles.save(p);
                log.debug("Promoted profile to ADMIN: id={} name='{}'", p.getId(), p.getName());
            } else {
                boolean ok = (p.getType() == ProfileType.ADMIN && "admin".equals(password))
                        || (p.getType() == ProfileType.MUSICIAN && ("user".equals(password) || "1234".equals(password)));
                if (!ok) {
                    log.debug("Invalid credentials for name='{}' type={}", name, p.getType());
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("message", "Invalid credentials"));
                }
            }
        }

        HttpSession session = request.getSession(true);
        session.setAttribute("profile", p);
        log.debug("Authenticated name='{}' id={} type={} sessionId={}", p.getName(), p.getId(), p.getType(), session.getId());

        // Generate and save a fresh CSRF token into the response cookie for the new session,
        // using the shared repo configured in SecurityConfig.
        CsrfToken newToken = csrfRepo.generateToken(request);
        csrfRepo.saveToken(newToken, request, response);

        // also set request attributes so filters like WriteCsrfCookieFilter can see it
        request.setAttribute(CsrfToken.class.getName(), newToken);
        request.setAttribute("_csrf", newToken);

        String xsrf = newToken != null ? newToken.getToken() : null;

        Map<String, Object> resp = new HashMap<>();
        resp.put("id", p.getId());
        resp.put("name", p.getName());
        resp.put("type", p.getType().name());
        if (xsrf != null) resp.put("xsrfToken", xsrf);

        return ResponseEntity.ok(resp);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            log.debug("Invalidating session id={}", session.getId());
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }
}