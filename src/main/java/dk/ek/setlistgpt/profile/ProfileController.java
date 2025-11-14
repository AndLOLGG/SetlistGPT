package dk.ek.setlistgpt.profile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) { this.profileService = profileService; }

    // MUSICIAN login (guests use public endpoints; no login needed there)
    @PostMapping("/login")
    public ResponseEntity<Profile> login(@RequestBody Profile profile, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) return ResponseEntity.ok((Profile) session.getAttribute("profile"));

        Profile authed = profileService.authenticateAndGetProfile(profile.getName(), profile.getPassword());
        if (authed != null) {
            session = request.getSession();
            session.setMaxInactiveInterval(1200);
            session.setAttribute("profile", authed);
            return ResponseEntity.ok(authed);
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    // Open signup for MUSICIAN (type is forced to MUSICIAN)
    @PostMapping("/signup")
    public ResponseEntity<Profile> signup(@RequestBody Profile profile) {
        profile.setType(ProfileType.MUSICIAN);
        Profile created = profileService.createProfile(profile);
        // 409 if username already exists
        if (created == null) return ResponseEntity.status(HttpStatus.CONFLICT).build();
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
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
        // 409 if username already exists
        if (created == null) return ResponseEntity.status(HttpStatus.CONFLICT).build();
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // Admin delete rules:
    // \- Only ADMIN can call this endpoint.
    // \- Main 'admin' user cannot be deleted.
    // \- ADMIN can delete themselves and any MUSICIAN.
    // \- Deleting self invalidates the session.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProfile(@PathVariable Long id, HttpServletRequest request) {
        if (ProfileType.ADMIN.verifyAccessLevel(request)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        HttpSession session = request.getSession(false);
        Profile actor = (Profile) (session != null ? session.getAttribute("profile") : null);

        Profile target = profileService.getProfileById(id);
        if (target == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        if (target.getType() == ProfileType.ADMIN && "admin".equalsIgnoreCase(target.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

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