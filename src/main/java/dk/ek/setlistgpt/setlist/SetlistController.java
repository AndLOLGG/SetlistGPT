package dk.ek.setlistgpt.setlist;

import dk.ek.setlistgpt.profile.Profile;
import dk.ek.setlistgpt.song.Song;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api")
public class SetlistController {

    private final SetlistService service;

    public SetlistController(SetlistService service) {
        this.service = service;
    }

    @GetMapping("/songs")
    public List<Song> getSongs() {
        return service.getAllSongs();
    }

    @PostMapping("/songs")
    public ResponseEntity<Song> createSong(@RequestBody Song song) {
        Song saved = service.createSong(song);
        return ResponseEntity.status(201).body(saved);
    }

    @GetMapping("/setlists")
    public List<SetlistSummaryDto> listSetlists() {
        return service.listSetlists();
    }

    @PostMapping("/setlist")
    public ResponseEntity<List<Song>> buildSetlist(@Valid @RequestBody SetlistRequest request,
                                                   HttpServletRequest http) {
        if (!service.validateInput(request.getTitle(), request.getArtist())) {
            return ResponseEntity.badRequest().build();
        }

        int m = Math.max(0, Math.min(59, request.getDurationMinutes()));
        int s = Math.max(0, Math.min(59, request.getDurationSeconds()));
        int duration = m * 60 + s;
        if (duration < 1 || duration > 59 * 60 + 59) {
            return ResponseEntity.badRequest().build();
        }

        List<Song> filtered = service.filterSongsByCriteria(
                request.getTitle(), request.getArtist(), request.getGenre(), null, request.getMood());

        List<Song> setlist = service.buildSetList(filtered, duration, request.getMood(), null);

        if (request.isAllowReuse()) {
            setlist = service.fillSetWithReusedSongs(setlist, duration, true, request.getMood(), null);
        }

        // Attach owner if logged in (admins and musicians can both create)
        Profile owner = null;
        var session = http.getSession(false);
        if (session != null) {
            Object p = session.getAttribute("profile");
            if (p instanceof Profile) owner = (Profile) p;
        }
        service.saveBuiltSetlist(owner, request.getTitle(), setlist);

        return ResponseEntity.ok(setlist);
    }

    @GetMapping
    public ResponseEntity<Void> apiRoot() {
        return ResponseEntity.status(303).header("Location", "/api/songs").build();
    }
}
