package dk.ek.setlistgpt.admin;

import dk.ek.setlistgpt.profile.Profile;
import dk.ek.setlistgpt.profile.ProfileRepository;
import dk.ek.setlistgpt.profile.ProfileType;
import dk.ek.setlistgpt.repertoire.Repertoire;
import dk.ek.setlistgpt.repertoire.RepertoireRepository;
import dk.ek.setlistgpt.setlist.Setlist;
import dk.ek.setlistgpt.setlist.SetlistRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ProfileRepository profiles;
    private final RepertoireRepository repertoires;
    private final SetlistRepository setlists;

    // Super admin guard: adjust name/id as needed.
    private static final Set<String> SUPER_ADMIN_NAMES = Set.of("admin");
    private static final Set<Long> SUPER_ADMIN_IDS = Set.of(1L);

    public AdminController(ProfileRepository profiles,
                           RepertoireRepository repertoires,
                           SetlistRepository setlists) {
        this.profiles = profiles;
        this.repertoires = repertoires;
        this.setlists = setlists;
    }

    @GetMapping("/profiles/grouped")
    public ResponseEntity<AdminProfilesGroupedDto> getGroupedProfiles(HttpServletRequest request) {
        // Require ADMIN session; deny otherwise.
        if (!ProfileType.ADMIN.verifyAccessLevel(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<AdminProfileSummaryDto> summaries = profiles.fetchAdminProfileSummaries();

        var admins = summaries.stream()
                .filter(s -> s.getType() == ProfileType.ADMIN)
                .sorted(Comparator.comparing(AdminProfileSummaryDto::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        var musicians = summaries.stream()
                .filter(s -> s.getType() == ProfileType.MUSICIAN)
                .sorted(Comparator.comparing(AdminProfileSummaryDto::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        Profile current = sessionProfile(request);
        AdminProfilesGroupedDto dto = new AdminProfilesGroupedDto(admins, musicians);
        dto.setTotalProfiles((long) summaries.size());
        dto.setAdminProfiles((long) admins.size());
        dto.setMusicianProfiles((long) musicians.size());
        dto.setRepertoires(summaries.stream().mapToLong(AdminProfileSummaryDto::getRepertoireCount).sum());
        dto.setSongs(summaries.stream().mapToLong(AdminProfileSummaryDto::getSongCount).sum());
        dto.setSetlists(summaries.stream().mapToLong(AdminProfileSummaryDto::getSetlistCount).sum());
        dto.setCurrentAdminName(current != null ? current.getName() : null);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/profiles/{profileId}")
    public ResponseEntity<AdminProfileDetailDto> profileDetail(@PathVariable Long profileId,
                                                               HttpServletRequest request) {
        if (!ProfileType.ADMIN.verifyAccessLevel(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var pOpt = profiles.findById(profileId);
        if (pOpt.isEmpty()) return ResponseEntity.notFound().build();
        Profile p = pOpt.get();

        List<AdminRepertoireSummaryDto> repDtos = repertoires.findByOwnerId(profileId).stream()
                .map(AdminRepertoireSummaryDto::from)
                .sorted(Comparator.comparing(AdminRepertoireSummaryDto::getTitle, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<AdminSetlistSummaryDto> setDtos = setlists.findByOwnerIdOrderByCreatedAtDesc(profileId).stream()
                .map(AdminSetlistSummaryDto::from)
                .toList();

        return ResponseEntity.ok(new AdminProfileDetailDto(p.getId(), p.getName(), repDtos, setDtos));
    }

    @GetMapping("/repertoires/{repertoireId}/songs")
    public ResponseEntity<List<AdminSongListItemDto>> repertoireSongs(@PathVariable Long repertoireId,
                                                                      HttpServletRequest request) {
        if (!ProfileType.ADMIN.verifyAccessLevel(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Optional<Repertoire> repOpt = repertoires.findById(repertoireId);
        if (repOpt.isEmpty()) return ResponseEntity.notFound().build();

        List<AdminSongListItemDto> songs = repOpt.get().getSongs().stream()
                .sorted(Comparator.comparing(s -> Optional.ofNullable(s.getTitle()).orElse(""), String.CASE_INSENSITIVE_ORDER))
                .map(AdminSongListItemDto::from)
                .toList();

        return ResponseEntity.ok(songs);
    }

    @GetMapping("/setlists/{setlistId}/songs")
    public ResponseEntity<List<AdminSongListItemDto>> setlistSongs(@PathVariable Long setlistId,
                                                                   HttpServletRequest request) {
        if (!ProfileType.ADMIN.verifyAccessLevel(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Optional<Setlist> slOpt = setlists.findById(setlistId);
        if (slOpt.isEmpty()) return ResponseEntity.notFound().build();

        List<AdminSongListItemDto> songs = slOpt.get().getItems().stream()
                .sorted(Comparator.comparingInt(i -> i.getPositionIndex() == null ? 0 : i.getPositionIndex()))
                .map(i -> AdminSongListItemDto.from(i.getSong()))
                .toList();

        return ResponseEntity.ok(songs);
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
