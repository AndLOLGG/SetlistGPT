package dk.ek.setlistgpt.profile;

import jakarta.annotation.PostConstruct;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final ProfileRepository repo;
    private final PasswordEncoder passwordEncoder;

    public ProfileService(ProfileRepository repo, PasswordEncoder passwordEncoder) {
        this.repo = repo;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    @Transactional
    public void seedAdminIfMissing() {
        // admin/admin (change and hash in production)
        if (!repo.existsByName("admin")) {
            Profile admin = Profile.builder()
                    .name("admin")
                    .password(passwordEncoder.encode("admin"))
                    .type(ProfileType.ADMIN)
                    .build();
            repo.save(admin);
        }
    }

    @Transactional(readOnly = true)
    public Profile authenticateAndGetProfile(String name, String password) {
        return repo.findByName(name)
                .filter(p -> {
                    String stored = p.getPassword();
                    return stored != null && password != null && passwordEncoder.matches(password, stored);
                })
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Profile getProfileByName(String name) {
        return repo.findByName(name).orElse(null);
    }

    @Transactional
    public Profile createProfile(Profile profile) {
        if (profile == null) throw new IllegalArgumentException("profile required");
        if (profile.getName() == null || profile.getName().isBlank()) throw new IllegalArgumentException("name is required");
        if (profile.getPassword() == null || profile.getPassword().isBlank()) throw new IllegalArgumentException("password is required");

        if (profile.getType() == null) profile.setType(ProfileType.MUSICIAN);
        // signal conflict to controller for HTTP 409
        if (repo.existsByName(profile.getName())) return null;

        // encode password before saving
        profile.setPassword(passwordEncoder.encode(profile.getPassword()));
        return repo.save(profile);
    }

    @Transactional(readOnly = true)
    public Profile getProfileById(Long id) {
        return repo.findById(id).orElse(null);
    }

    @Transactional
    public Profile updateProfile(Profile profile) {
        if (profile.getId() == null) return null;
        var existingOpt = repo.findById(profile.getId());
        if (existingOpt.isEmpty()) return null;
        Profile existing = existingOpt.get();

        // Update fields safely: name, type, password (encode if provided)
        if (profile.getName() != null && !profile.getName().isBlank()) {
            existing.setName(profile.getName());
        }
        if (profile.getType() != null) {
            existing.setType(profile.getType());
        }
        if (profile.getPassword() != null && !profile.getPassword().isBlank()) {
            existing.setPassword(passwordEncoder.encode(profile.getPassword()));
        }
        return repo.save(existing);
    }

    @Transactional
    public void deleteProfile(Profile profile) {
        repo.delete(profile);
    }
}