package dk.ek.setlistgpt.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.Map;

/**
 * Single controller to serve the SPA entrypoint for client-side routes
 * and to act as the application ErrorController so the SPA index is returned
 * for error requests (avoids BasicErrorController mapping conflict).
 */
@Controller
public class SpaController implements ErrorController {

    private final Logger log = LoggerFactory.getLogger(SpaController.class);
    private final ErrorAttributes errorAttributes;

    public SpaController(ErrorAttributes errorAttributes) {
        this.errorAttributes = errorAttributes;
    }

    @GetMapping({
            "/",
            "/profile",
            "/profile/**",
            "/repertoires",
            "/repertoires/**",
            "/setlists",
            "/setlists/**",
            "/songs",
            "/songs/**"
    })
    public String index() {
        return "index";
    }

    @RequestMapping("${server.error.path:${error.path:/error}}")
    public String handleError(HttpServletRequest request, HttpServletResponse response, Model model) {
        ServletWebRequest swr = new ServletWebRequest(request);
        Map<String, Object> attrs = errorAttributes.getErrorAttributes(swr, ErrorAttributeOptions.defaults());

        // Try to get the original status code from the request attributes (forwarded)
        Integer forwardedStatus = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Integer status = forwardedStatus != null ? forwardedStatus : (Integer) attrs.get("status");
        String uri = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (uri == null) uri = request.getRequestURI();

        // Treat 404 as a normal SPA route: return index with 200 and log at INFO.
        if (status != null && status == HttpServletResponse.SC_NOT_FOUND) {
            log.info("SPA request forwarded to /error (original {} -> 404). Serving index for client routing: {}", uri, attrs.get("message"));
            response.setStatus(HttpServletResponse.SC_OK);
        } else {
            log.error("Server error at {}: status={}, error={}, message={}",
                    uri,
                    status,
                    attrs.get("error"),
                    attrs.get("message"));
            // keep response status as-is for real server errors
            if (status != null) response.setStatus(status);
        }

        model.addAttribute("errorStatus", attrs.get("status"));
        model.addAttribute("errorMessage", attrs.get("message"));
        return "index";
    }
}