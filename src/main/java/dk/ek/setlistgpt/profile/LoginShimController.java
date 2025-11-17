package dk.ek.setlistgpt.profile;

import dk.ek.setlistgpt.auth.AuthController;
import dk.ek.setlistgpt.auth.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Fallback login endpoint used only if the real AuthController is not present.
 * This prevents ambiguous mapping for POST /api/login.
 */
@ConditionalOnMissingBean(AuthController.class)
@RestController
@RequestMapping("/api")
public class LoginShimController {

    private static final Logger log = LoggerFactory.getLogger(LoginShimController.class);

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest body,
                                                     HttpServletRequest request) {
        log.warn("LoginShimController invoked; real AuthController not present.");
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of("message", "Login shim; real auth not available"));
    }
}