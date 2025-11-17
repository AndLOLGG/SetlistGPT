package dk.ek.setlistgpt.security;

import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

@RestController
public class CsrfController {

    private final CookieCsrfTokenRepository csrfRepo;

    public CsrfController(CookieCsrfTokenRepository csrfRepo) {
        this.csrfRepo = csrfRepo;
    }

    @GetMapping("/api/csrf")
    public ResponseEntity<Map<String, String>> csrf(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken token = csrfRepo.generateToken(request);
        csrfRepo.saveToken(token, request, response);

        request.setAttribute(CsrfToken.class.getName(), token);
        request.setAttribute("_csrf", token);

        return ResponseEntity.ok(Map.of("token", token.getToken()));
    }
}