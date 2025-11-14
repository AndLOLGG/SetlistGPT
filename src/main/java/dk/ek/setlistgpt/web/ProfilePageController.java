package dk.ek.setlistgpt.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ProfilePageController {

    @GetMapping("/profile/create")
    public String createProfilePage() {
        return "profile-create";
    }
}