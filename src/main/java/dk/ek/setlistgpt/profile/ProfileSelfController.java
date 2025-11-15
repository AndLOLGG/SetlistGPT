package dk.ek.setlistgpt.profile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RestController
@RequestMapping("/api/profile")
public class ProfileSelfController {

    private final ProfileRepository profiles;

    public ProfileSelfController(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteOwnProfile(HttpServletRequest request) {
        Profile current = sessionProfile(request);
        if (current == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (current.getType() != ProfileType.MUSICIAN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!profiles.existsById(current.getId())) {
            return ResponseEntity.notFound().build();
        }
        profiles.deleteById(current.getId());
        return ResponseEntity.noContent().build();
    }

    private static Profile sessionProfile(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object obj = session.getAttribute("profile");
        return (obj instanceof Profile) ? (Profile) obj : null;
    }
}