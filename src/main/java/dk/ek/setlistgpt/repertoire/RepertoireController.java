package dk.ek.setlistgpt.repertoire;

import dk.ek.setlistgpt.profile.Profile;
import dk.ek.setlistgpt.song.Song;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * RepertoireController: list endpoint now returns:
 * - if HTTP session contains a 'profile' -> repertoires owned by that profile (My Repertoires)
 * - otherwise -> only PUBLIC repertoires (public browser)
 *
 * Mutating endpoints remain protected by security.
 */
@RestController
@RequestMapping("/api/repertoires")
public class RepertoireController {

    private final RepertoireRepository repo;
    private final RepertoireService repertoireService;

    public RepertoireController(RepertoireRepository repo, RepertoireService repertoireService) {
        this.repo = repo;
        this.repertoireService = repertoireService;
    }

    // Public endpoint: list PUBLIC repertoires
    @GetMapping("/public")
    public List<Repertoire> listPublic() {
        return repo.findByVisibility(RepertoireVisibility.PUBLIC);
    }

    // List repertoires: if session has profile -> return owner's repertoires; otherwise return PUBLIC repertoires.
    @GetMapping
    public List<Repertoire> listAll(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object obj = session.getAttribute("profile");
            if (obj instanceof Profile) {
                Profile p = (Profile) obj;
                if (p.getId() != null) {
                    // return only the owner's repertoires (keeps PRIVATE data private)
                    return repo.findByOwnerId(p.getId());
                }
            }
        }
        // fallback: public listing
        return repo.findByVisibility(RepertoireVisibility.PUBLIC);
    }

    // Create a new repertoire (temporary: not scoped to user; secure later)
    @PostMapping
    public ResponseEntity<Repertoire> create(@RequestBody Repertoire body) {
        if (body == null || body.getName() == null || body.getName().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Repertoire r = new Repertoire();
        r.setName(body.getName().trim());
        // default PRIVATE unless explicitly PUBLIC
        r.setVisibility(body.getVisibility() == RepertoireVisibility.PUBLIC
                ? RepertoireVisibility.PUBLIC
                : RepertoireVisibility.PRIVATE);
        Repertoire saved = repo.save(r);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // Get single repertoire (includes songs via JSON managed reference)
    @GetMapping("/{id}")
    public ResponseEntity<Repertoire> getOne(@PathVariable Long id) {
        return repo.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/songs")
    public ResponseEntity<List<Song>> getSongs(@PathVariable Long id) {
        return repo.findById(id)
                .map(r -> ResponseEntity.ok(r.getSongs()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/visibility")
    public ResponseEntity<Repertoire> updateVisibility(
            @PathVariable Long id,
            @RequestParam RepertoireVisibility visibility) {
        return repo.findById(id)
                .map(r -> {
                    r.setVisibility(visibility);
                    return ResponseEntity.ok(repo.save(r));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{repertoireId}/songs/{songId}")
    public ResponseEntity<Song> addSongToRepertoire(@PathVariable Long repertoireId, @PathVariable Long songId) {
        try {
            Song updated = repertoireService.addExistingSongToRepertoire(repertoireId, songId);
            if (updated == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            return ResponseEntity.ok(updated);
        } catch (IllegalStateException conflict) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @DeleteMapping("/{repertoireId}/songs/{songId}")
    public ResponseEntity<Void> removeSongFromRepertoire(@PathVariable Long repertoireId, @PathVariable Long songId) {
        boolean removed = repertoireService.removeSongFromRepertoire(repertoireId, songId);
        if (!removed) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        return ResponseEntity.noContent().build();
    }
}