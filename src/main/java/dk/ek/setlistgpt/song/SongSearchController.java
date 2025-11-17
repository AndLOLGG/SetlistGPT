package dk.ek.setlistgpt.song;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Simple tolerant search endpoint for songs.
 * Accepts optional query params: artist, genre, mood.
 * Returns a JSON array of objects with { id, title, artist, durationInSeconds }.
 */
@RestController
@RequestMapping("/api/songs")
public class SongSearchController {

    private final SongRepository songs;

    public SongSearchController(SongRepository songs) {
        this.songs = songs;
    }

    @GetMapping("/search")
    public List<SongDto> search(
            @RequestParam(required = false) String artist,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String mood
    ) {
        String artistNeedle = normalize(artist);
        Set<SongGenre> allowedGenres = SongGenreGroup.resolve(genre);
        SongMood requestedMood = parseMood(mood);

        return songs.findAll().stream()
                .filter(s -> matchArtist(s, artistNeedle))
                .filter(s -> matchGenre(s, allowedGenres))
                .filter(s -> matchMood(s, requestedMood))
                .sorted(Comparator.comparing(
                        (Song s) -> Optional.ofNullable(s.getTitle()).orElse(""),
                        String.CASE_INSENSITIVE_ORDER
                ))
                .map(SongDto::from)
                .collect(Collectors.toList());
    }

    private static boolean matchArtist(Song s, String needle) {
        if (needle == null) return true;
        return containsIgnoreCase(s.getArtist(), needle);
    }

    private static boolean matchGenre(Song s, Set<SongGenre> allowed) {
        if (allowed == null || allowed.isEmpty()) return true;
        return s.getGenre() != null && allowed.contains(s.getGenre());
    }

    private static boolean matchMood(Song s, SongMood requested) {
        if (requested == null) return true;
        return s.getMood() != null && s.getMood().compatibleWith(requested);
    }

    private static String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null) return false;
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static SongMood parseMood(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return SongMood.valueOf(raw.trim().replaceAll("[\\s\\-]+", "_").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}