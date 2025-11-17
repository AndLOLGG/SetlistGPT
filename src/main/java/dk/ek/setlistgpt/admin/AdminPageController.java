package dk.ek.setlistgpt.admin;

import dk.ek.setlistgpt.profile.Profile;
import dk.ek.setlistgpt.profile.ProfileType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminPageController {

    private static final Logger log = LoggerFactory.getLogger(AdminPageController.class);

    @GetMapping({"/admin", "/admin/"})
    public String admin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Profile profile = (session != null) ? (Profile) session.getAttribute("profile") : null;
        log.debug("/admin requested; profileType={}", profile != null ? profile.getType() : null);
        if (profile == null || profile.getType() != ProfileType.ADMIN) {
            return "redirect:/";
        }
        return "admin";
    }
}