package dk.ek.setlistgpt.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.Map;

/**
 * SPA entry controller and error bridge.
 * Serves the index page for SPA routes and ensures API errors are not rendered as HTML.
 */
@Controller
public class SpaController implements ErrorController {

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

    @RequestMapping(
            value = "${server.error.path:${error.path:/error}}",
            produces = MediaType.TEXT_HTML_VALUE
    )
    public String handleErrorHtml(HttpServletRequest request, HttpServletResponse response, Model model) {
        var swr = new ServletWebRequest(request);
        Map<String, Object> attrs = errorAttributes.getErrorAttributes(swr, ErrorAttributeOptions.defaults());
        Integer forwardedStatus = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = forwardedStatus != null ? forwardedStatus : (Integer) attrs.getOrDefault("status", 500);
        String uri = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (uri == null) uri = request.getRequestURI();

        // Do not hijack API errors to HTML
        if (uri != null && uri.startsWith("/api/")) {
            response.setStatus(status);
            return null; // let default error rendering proceed (JSON for API)
        }

        if (status == HttpServletResponse.SC_NOT_FOUND) {
            log.info("SPA 404 -> serving index for {}", uri);
            response.setStatus(HttpServletResponse.SC_OK);
        } else {
            log.error("Server error at {} status={} error={} message={}", uri, status, attrs.get("error"), attrs.get("message"));
            response.setStatus(status);
        }
        model.addAttribute("errorStatus", attrs.get("status"));
        model.addAttribute("errorMessage", attrs.get("message"));
        return "index";
    }

    @ResponseBody
    @RequestMapping(
            value = "${server.error.path:${error.path:/error}}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> handleErrorJson(HttpServletRequest request) {
        var swr = new ServletWebRequest(request);
        Map<String, Object> attrs = errorAttributes.getErrorAttributes(swr, ErrorAttributeOptions.defaults());
        Integer forwardedStatus = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = HttpStatus.resolve(
                forwardedStatus != null ? forwardedStatus : (Integer) attrs.getOrDefault("status", 500)
        );
        return ResponseEntity.status(status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR).body(attrs);
    }
}