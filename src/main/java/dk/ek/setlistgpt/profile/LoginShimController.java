package dk.ek.setlistgpt.profile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Shim controller to handle /api/login requests with JSON or form data,
 * authenticating and creating session as needed.
 */
@RestController
@RequestMapping("/api")
public class LoginShimController {

    private final Logger log = LoggerFactory.getLogger(LoginShimController.class);
    private final ProfileService profileService;

    public LoginShimController(ProfileService profileService) {
        this.profileService = profileService;
    }

    // JSON payload: { "name": "...", "password": "..." } or { "username": "...", "password": "..." }
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> loginJson(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        log.debug("LoginShim /api/login JSON, contentType={}, bodyKeys={}",
                request.getContentType(), body != null ? body.keySet() : null);

        if (body == null) {
            return badRequest("request body required");
        }
        String name = str(body.getOrDefault("name", body.get("username")));
        String password = str(body.get("password"));
        return doLogin(name, password, request);
    }

    // Form post: name=username or username=username, password=...
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> loginForm(@RequestParam Map<String, String> form, HttpServletRequest request) {
        log.debug("LoginShim /api/login FORM, contentType={}, formKeys={}",
                request.getContentType(), form != null ? form.keySet() : null);

        String name = str(form.getOrDefault("name", form.get("username")));
        String password = str(form.get("password"));
        return doLogin(name, password, request);
    }

    private ResponseEntity<?> doLogin(String name, String password, HttpServletRequest request) {
        String path = "/api/profile/login";

        // existing session with profile -> OK
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            Profile inSession = (Profile) existing.getAttribute("profile");
            if (inSession != null) return ResponseEntity.ok(inSession);
            existing.invalidate();
        }

        if (isBlank(name) || isBlank(password)) {
            return badRequest("name and password are required");
        }

        Profile authed = profileService.authenticateAndGetProfile(name.trim(), password.trim());
        if (authed == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("path", path, "status", 401, "error", "invalid credentials"));
        }

        HttpSession session = request.getSession();
        session.setMaxInactiveInterval(1200);
        session.setAttribute("profile", authed);
        return ResponseEntity.ok(authed);
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private static String str(Object o) { return o == null ? null : String.valueOf(o); }

    private static ResponseEntity<Map<String, Object>> badRequest(String msg) {
        return ResponseEntity.badRequest().body(Map.of("path", "/api/profile/login", "status", 400, "error", msg));
    }
}