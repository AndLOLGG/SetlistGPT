package dk.ek.setlistgpt.setlist;

import lombok.*;
import dk.ek.setlistgpt.song.Song;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
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

    @PostMapping("/setlist")
    public ResponseEntity<List<Song>> buildSetlist(
            @Valid @RequestBody SetlistRequest request) {
        if (!service.validateInput(request.getTitle(), request.getArtist())) {
            return ResponseEntity.badRequest().build();
        }

        int m = Math.max(0, Math.min(59, request.getDurationMinutes()));
        int s = Math.max(0, Math.min(59, request.getDurationSeconds()));
        int duration = m * 60 + s;
        if (duration < 1 || duration > 59 * 60 + 59) {
            return ResponseEntity.badRequest().build();
        }

        List<Song> filtered = service.filterSongsByCriteria(request.getTitle(), request.getArtist(), request.getGenre(), request.getBpm(), request.getMood());

        // Pass desired mood and bpm (may be null) — service treats them as optional
        List<Song> setlist = service.buildSetList(filtered, duration, request.getMood(), request.getBpm());

        if (request.isAllowReuse()) {
            setlist = service.fillSetWithReusedSongs(setlist, duration, true, request.getMood(), request.getBpm());
        }

        return ResponseEntity.ok(setlist);
    }

    @GetMapping
    public ResponseEntity<Void> apiRoot() {
        return ResponseEntity.status(303).header("Location", "/api/songs").build();
    }
}