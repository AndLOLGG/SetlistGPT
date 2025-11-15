package dk.ek.setlistgpt.admin;

import dk.ek.setlistgpt.profile.Profile;
import dk.ek.setlistgpt.profile.ProfileRepository;
import dk.ek.setlistgpt.profile.ProfileType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@CrossOrigin
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ProfileRepository profiles;

    // Super admin guard: adjust name/id as needed.
    private static final Set<String> SUPER_ADMIN_NAMES = Set.of("admin");
    private static final Set<Long> SUPER_ADMIN_IDS = Set.of(1L);

    public AdminController(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/profiles/grouped")
    public ResponseEntity<AdminProfilesGroupedDto> getGroupedProfiles(HttpServletRequest request) {
        // Require ADMIN session; deny otherwise.
        if (!ProfileType.ADMIN.verifyAccessLevel(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // JPA projection: single query builds all per-profile counts (replaces manual EntityManager queries).
        List<AdminProfileSummaryDto> summaries = profiles.fetchAdminProfileSummaries();

        var admins = summaries.stream()
                .filter(s -> s.getType() == ProfileType.ADMIN)
                .sorted(Comparator.comparing(AdminProfileSummaryDto::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        var musicians = summaries.stream()
                .filter(s -> s.getType() == ProfileType.MUSICIAN)
                .sorted(Comparator.comparing(AdminProfileSummaryDto::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        // Populate optional aggregated fields (computed in-memory; no extra queries).
        Profile current = sessionProfile(request);
        long totalProfiles = summaries.size();
        long repertoireTotal = summaries.stream().mapToLong(AdminProfileSummaryDto::getRepertoireCount).sum();
        long songTotal = summaries.stream().mapToLong(AdminProfileSummaryDto::getSongCount).sum();
        long setlistTotal = summaries.stream().mapToLong(AdminProfileSummaryDto::getSetlistCount).sum();

        AdminProfilesGroupedDto dto = new AdminProfilesGroupedDto(admins, musicians);
        dto.setTotalProfiles(totalProfiles);
        dto.setAdminProfiles((long) admins.size());
        dto.setMusicianProfiles((long) musicians.size());
        dto.setRepertoires(repertoireTotal);
        dto.setSongs(songTotal);
        dto.setSetlists(setlistTotal);
        dto.setCurrentAdminName(current != null ? current.getName() : null);

        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/profiles/{id}")
    public ResponseEntity<Void> deleteProfileAsAdmin(@PathVariable Long id, HttpServletRequest request) {
        // Only admins can delete.
        if (!ProfileType.ADMIN.verifyAccessLevel(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var targetOpt = profiles.findById(id);
        if (targetOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Profile target = targetOpt.get();

        // Protect THE admin admin.
        if (isSuperAdmin(target)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        profiles.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private static Profile sessionProfile(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object obj = session.getAttribute("profile");
        return (obj instanceof Profile) ? (Profile) obj : null;
    }

    private static boolean isSuperAdmin(Profile p) {
        if (p == null) return false;
        if (p.getType() != ProfileType.ADMIN) return false;
        if (p.getId() != null && SUPER_ADMIN_IDS.contains(p.getId())) return true;
        String name = p.getName();
        return name != null && SUPER_ADMIN_NAMES.contains(name.trim().toLowerCase(Locale.ROOT));
    }
}
