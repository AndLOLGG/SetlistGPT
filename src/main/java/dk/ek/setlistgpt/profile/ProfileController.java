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

@CrossOrigin
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final Logger log = LoggerFactory.getLogger(ProfileController.class);
    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) { this.profileService = profileService; }

    // MUSICIAN login (guests use public endpoints; no login needed there)
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> login(@RequestBody Profile body, HttpServletRequest request) {
        String path = "/api/profile/login";

        // If a session exists but has no profile, invalidate it to avoid returning 200 with null
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            Profile inSession = (Profile) existing.getAttribute("profile");
            if (inSession != null) return ResponseEntity.ok(inSession);
            existing.invalidate();
        }

        if (body == null) {
            return ResponseEntity.badRequest().body(Map.of("path", path, "status", 400, "error", "request body required"));
        }
        String name = body.getName() != null ? body.getName().trim() : "";
        String password = body.getPassword() != null ? body.getPassword().trim() : "";
        if (name.isBlank() || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("path", path, "status", 400, "error", "name and password are required"));
        }

        Profile authed = profileService.authenticateAndGetProfile(name, password);
        if (authed == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("path", path, "status", 401, "error", "invalid credentials"));
        }

        HttpSession session = request.getSession();
        session.setMaxInactiveInterval(1200);
        session.setAttribute("profile", authed);
        return ResponseEntity.ok(authed);
    }

    // Open signup for MUSICIAN (type is forced to MUSICIAN)
    @PostMapping(value = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> signup(@RequestBody Profile profile) {
        String path = "/api/profile/signup";

        if (profile == null
                || profile.getName() == null || profile.getName().trim().isEmpty()
                || profile.getPassword() == null || profile.getPassword().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "path", path,
                    "status", 400,
                    "error", "name and password are required"
            ));
        }

        // Sanitize client input: prevent setting id/type from client
        profile.setId(null);
        profile.setName(profile.getName().trim());
        profile.setType(ProfileType.MUSICIAN);

        try {
            Profile created = profileService.createProfile(profile);
            if (created == null) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "path", path,
                        "status", 409,
                        "error", "username already exists"
                ));
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(Map.of(
                    "path", path,
                    "status", 400,
                    "error", iae.getMessage()
            ));
        } catch (Exception e) {
            log.error("Signup error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "path", path,
                    "status", 500,
                    "error", "Internal Server Error"
            ));
        }
    }

    // Return the current session profile (401 if not logged in)
    @GetMapping("/me")
    public ResponseEntity<Profile> me(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Profile p = (Profile) session.getAttribute("profile");
        if (p == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(p);
    }

    // Admin: lookup by name (no auto‑create)
    @GetMapping("/by-name")
    public ResponseEntity<Profile> getByName(@RequestParam String name, HttpServletRequest request) {
        if (ProfileType.ADMIN.verifyAccessLevel(request)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Profile existing = profileService.getProfileByName(name);
        if (existing == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        return ResponseEntity.ok(existing);
    }

    // Admin: lookup by id
    @GetMapping
    public ResponseEntity<Profile> getById(@RequestParam Long id, HttpServletRequest request) {
        if (ProfileType.ADMIN.verifyAccessLevel(request)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Profile existing = profileService.getProfileById(id);
        if (existing == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        return ResponseEntity.ok(existing);
    }

    // Admin: update any profile
    @PutMapping("/update")
    public ResponseEntity<Profile> updateProfile(@RequestBody Profile profile, HttpServletRequest request) {
        if (ProfileType.ADMIN.verifyAccessLevel(request)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Profile updated = profileService.updateProfile(profile);
        if (updated == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(updated);
    }

    // Admin: create ADMIN or MUSICIAN (reject others; default to MUSICIAN if missing)
    @PostMapping("/create")
    public ResponseEntity<Profile> createNewProfile(@RequestBody Profile profile, HttpServletRequest request) {
        if (ProfileType.ADMIN.verifyAccessLevel(request)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        ProfileType desired = profile.getType();
        if (desired == null) {
            profile.setType(ProfileType.MUSICIAN);
        } else if (desired != ProfileType.ADMIN && desired != ProfileType.MUSICIAN) {
            return ResponseEntity.badRequest().build();
        }

        Profile created = profileService.createProfile(profile);
        if (created == null) return ResponseEntity.status(HttpStatus.CONFLICT).build();
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // Admin delete rules documented in method body below
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProfile(@PathVariable Long id, HttpServletRequest request) {
        if (ProfileType.ADMIN.verifyAccessLevel(request)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        HttpSession session = request.getSession(false);
        Profile actor = (Profile) (session != null ? session.getAttribute("profile") : null);

        Profile target = profileService.getProfileById(id);
        if (target == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        // Protect the main 'admin' user from deletion
        if (target.getType() == ProfileType.ADMIN && "admin".equalsIgnoreCase(target.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Only ADMIN can delete ADMIN and only themselves; ADMIN can delete any MUSICIAN
        if (target.getType() == ProfileType.ADMIN) {
            if (actor == null || !target.getId().equals(actor.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        } else if (target.getType() != ProfileType.MUSICIAN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        profileService.deleteProfile(target);

        if (actor != null && actor.getId().equals(target.getId()) && session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    // Logout clears the session
    @PutMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return ResponseEntity.noContent().build();
    }
}