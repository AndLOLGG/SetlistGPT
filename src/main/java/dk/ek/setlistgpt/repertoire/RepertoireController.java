package dk.ek.setlistgpt.repertoire;

import dk.ek.setlistgpt.song.Song;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/repertoires")
public class RepertoireController {

    private final RepertoireRepository repo;
    private final RepertoireService repertoireService;

    public RepertoireController(RepertoireRepository repo, RepertoireService repertoireService) {
        this.repo = repo;
        this.repertoireService = repertoireService;
    }

    // Public endpoint: Guests can browse public setlists without login
    @GetMapping("/public")
    public List<Repertoire> listPublic() {
        return repo.findByVisibility(RepertoireVisibility.PUBLIC);
    }

    // Change visibility (secure this later)
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

    // Link an existing song to a repertoire (uses Repertoire.addSong(...)).
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

    // Unlink/remove a song from a repertoire (uses Repertoire.removeSong(...)).
    @DeleteMapping("/{repertoireId}/songs/{songId}")
    public ResponseEntity<Void> removeSongFromRepertoire(@PathVariable Long repertoireId, @PathVariable Long songId) {
        boolean removed = repertoireService.removeSongFromRepertoire(repertoireId, songId);
        if (!removed) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        return ResponseEntity.noContent().build();
    }
}