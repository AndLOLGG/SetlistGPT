package dk.ek.setlistgpt.profile;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final ProfileRepository repo;

    public ProfileService(ProfileRepository repo) {
        this.repo = repo;
    }

    @PostConstruct
    @Transactional
    public void seedAdminIfMissing() {
        // admin/admin (change and hash in production)
        if (!repo.existsByName("admin")) {
            Profile admin = Profile.builder()
                    .name("admin")
                    .password("admin")
                    .type(ProfileType.ADMIN)
                    .build();
            repo.save(admin);
        }
    }

    @Transactional(readOnly = true)
    public Profile authenticateAndGetProfile(String name, String password) {
        return repo.findByName(name)
                .filter(p -> p.getPassword().equals(password))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Profile getProfileByName(String name) {
        return repo.findByName(name).orElse(null);
    }

    @Transactional
    public Profile createProfile(Profile profile) {
        if (profile.getType() == null) profile.setType(ProfileType.MUSICIAN);
        // signal conflict to controller for HTTP 409
        if (repo.existsByName(profile.getName())) return null;
        return repo.save(profile);
    }

    @Transactional(readOnly = true)
    public Profile getProfileById(Long id) {
        return repo.findById(id).orElse(null);
    }

    @Transactional
    public Profile updateProfile(Profile profile) {
        if (profile.getId() == null) return null;
        return repo.save(profile);
    }

    @Transactional
    public void deleteProfile(Profile profile) {
        repo.delete(profile);
    }
}