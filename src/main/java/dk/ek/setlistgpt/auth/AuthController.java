package dk.ek.setlistgpt.auth;

import dk.ek.setlistgpt.profile.Profile;
import dk.ek.setlistgpt.profile.ProfileService;
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
 * Session login that delegates password verification to ProfileService (uses PasswordEncoder).
 * Does NOT create or promote ADMINs — signup must use POST /api/profile/signup or admin-only APIs.
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final ProfileService profileService;
    private final CookieCsrfTokenRepository csrfRepo;

    public AuthController(ProfileService profileService, CookieCsrfTokenRepository csrfRepo) {
        this.profileService = profileService;
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

        Profile p = profileService.authenticateAndGetProfile(name, password);
        if (p == null) {
            log.debug("Authentication failed for name='{}'", name);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid credentials"));
        }

        HttpSession session = request.getSession(true);
        session.setAttribute("profile", p);
        log.debug("Authenticated name='{}' id={} type={} sessionId={}", p.getName(), p.getId(), p.getType(), session.getId());

        // Generate and save a fresh CSRF token into the response cookie for the new session.
        CsrfToken newToken = csrfRepo.generateToken(request);
        csrfRepo.saveToken(newToken, request, response);

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