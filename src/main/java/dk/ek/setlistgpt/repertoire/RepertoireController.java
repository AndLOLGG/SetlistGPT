package dk.ek.setlistgpt.repertoire;

import dk.ek.setlistgpt.profile.Profile;
import dk.ek.setlistgpt.song.Song;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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

    // Helper: sort by owner name, then repertoire name, then song count (desc)
    private List<Repertoire> sortPublicList(List<Repertoire> list) {
        return list.stream()
                .sorted(Comparator
                        .comparing((Repertoire r) -> {
                            String on = (r.getOwner() != null && r.getOwner().getName() != null) ? r.getOwner().getName().toLowerCase() : "";
                            return on;
                        })
                        .thenComparing(r -> (r.getName() != null ? r.getName().toLowerCase() : ""))
                        .thenComparing(Comparator.comparingInt((Repertoire r) -> (r.getSongs() != null ? r.getSongs().size() : 0)).reversed())
                ).collect(Collectors.toList());
    }

    // Public endpoint: list PUBLIC repertoires (sorted, fetch owner & songs via repository)
    @GetMapping("/public")
    public List<Repertoire> listPublic() {
        List<Repertoire> reps = repo.findByVisibility(RepertoireVisibility.PUBLIC);
        return sortPublicList(reps);
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
                    List<Repertoire> owned = repo.findByOwnerId(p.getId());
                    // sort owner's list by name then song-count desc
                    return owned.stream()
                            .sorted(Comparator
                                    .comparing((Repertoire r) -> (r.getName() != null ? r.getName().toLowerCase() : ""))
                                    .thenComparing(Comparator.comparingInt((Repertoire r) -> (r.getSongs() != null ? r.getSongs().size() : 0)).reversed())
                            ).collect(Collectors.toList());
                }
            }
        }
        // fallback: public listing
        List<Repertoire> reps = repo.findByVisibility(RepertoireVisibility.PUBLIC);
        return sortPublicList(reps);
    }

    // Create a new repertoire: attach owner (if session) and attach incoming songs to the new repertoire.
    @PostMapping
    public ResponseEntity<Repertoire> create(@RequestBody Repertoire body, HttpServletRequest request) {
        if (body == null || body.getName() == null || body.getName().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Repertoire r = new Repertoire();
        r.setName(body.getName().trim());
        r.setVisibility(body.getVisibility() == RepertoireVisibility.PUBLIC
                ? RepertoireVisibility.PUBLIC
                : RepertoireVisibility.PRIVATE);

        // attach owner from session if present
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object obj = session.getAttribute("profile");
            if (obj instanceof Profile) {
                r.setOwner((Profile) obj);
            }
        }

        // Attach incoming songs properly so each Song.repertoire is set
        if (body.getSongs() != null && !body.getSongs().isEmpty()) {
            for (Song incoming : body.getSongs()) {
                if (incoming == null) continue;
                Song s = new Song();
                s.setTitle(incoming.getTitle());
                s.setArtist(incoming.getArtist());
                s.setGenre(incoming.getGenre());
                s.setBpm(incoming.getBpm());
                s.setMood(incoming.getMood());
                s.setDurationMinutes(incoming.getDurationMinutes());
                s.setDurationSeconds(incoming.getDurationSeconds());
                r.addSong(s);
            }
        }

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