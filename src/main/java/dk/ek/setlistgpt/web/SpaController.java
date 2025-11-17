package dk.ek.setlistgpt.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.Map;

@Controller
public class SpaController {

    private final Logger log = LoggerFactory.getLogger(SpaController.class);
    private final ErrorAttributes errorAttributes;

    public SpaController(ErrorAttributes errorAttributes) {
        this.errorAttributes = errorAttributes;
    }

    @GetMapping({
            "/", "/index", "/index.html",
            "/profile", "/profile/**",
            "/repertoires", "/repertoires/**",
            "/setlists", "/setlists/**",
            "/songs", "/songs/**"
    })
    public String indexRoot() {
        return "index";
    }

    // Serve the SPA for browser navigations (Accept: text/html)
    @GetMapping(value = "/api/login", produces = MediaType.TEXT_HTML_VALUE)
    public String getApiLoginHtml(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        String ua = request.getHeader("User-Agent");
        String accept = request.getHeader("Accept");
        String remote = request.getRemoteAddr();
        String sessionId = (request.getSession(false) != null) ? request.getSession(false).getId() : null;
        log.info("GET /api/login (html) - referer={} accept={} ua={} remote={} sessionId={}",
                referer, accept, ua, remote, sessionId);

        // Serve index so the SPA can handle routing client-side.
        return "index";
    }

    // Preserve JSON response for API clients (non-browser)
    @ResponseBody
    @GetMapping(value = "/api/login", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getApiLoginJson(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        String ua = request.getHeader("User-Agent");
        String accept = request.getHeader("Accept");
        String remote = request.getRemoteAddr();
        String sessionId = (request.getSession(false) != null) ? request.getSession(false).getId() : null;

        log.info("GET /api/login (json) - referer={} accept={} ua={} remote={} sessionId={}",
                referer, accept, ua, remote, sessionId);

        Map<String, Object> body = Map.of(
                "error", "method_not_allowed",
                "message", "Authentication endpoint accepts POST only. POST credentials to /api/login or /login."
        );
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
    }

    // Keep only the JSON error handler to avoid conflicting with Spring Boot's BasicErrorController which
    // already provides an HTML error handler. API clients still receive JSON error payloads.
    @ResponseBody
    @RequestMapping(
            value = "${server.error.path:${error.path:/error}}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> handleErrorJson(HttpServletRequest request) {
        var swr = new ServletWebRequest(request);
        Map<String, Object> attrs = errorAttributes.getErrorAttributes(swr, ErrorAttributeOptions.defaults());
        Integer forwardedStatus = (Integer) request.getAttribute("jakarta.servlet.error.status_code");
        // backwards-compatible key fallback
        if (forwardedStatus == null) {
            forwardedStatus = (Integer) request.getAttribute("javax.servlet.error.status_code");
        }
        HttpStatus status = HttpStatus.resolve(
                forwardedStatus != null ? forwardedStatus : (Integer) attrs.getOrDefault("status", 500)
        );
        return ResponseEntity.status(status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR).body(attrs);
    }
}