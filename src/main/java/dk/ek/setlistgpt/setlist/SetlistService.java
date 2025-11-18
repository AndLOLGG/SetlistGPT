package dk.ek.setlistgpt.setlist;

import dk.ek.setlistgpt.profile.Profile;
import dk.ek.setlistgpt.song.Song;
import dk.ek.setlistgpt.song.SongGenre;
import dk.ek.setlistgpt.song.SongGenreGroup;
import dk.ek.setlistgpt.song.SongMood;
import dk.ek.setlistgpt.song.SongRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service handling song retrieval, filtering, setlist building and persistence.
 *
 * - Builder methods produce a list of Song entities that can be persisted as a Setlist.
 * - Persistence helpers create Setlist and SetlistItem entities and keep both sides of relationships in sync.
 * - New: convenience method to create a manual setlist from an ordered list of Song ids.
 */
@Service
public class SetlistService {

    private final SongRepository songRepository;
    private final MoodCalculator moodCalculator;
    private final SetlistRepository setlistRepository;

    public SetlistService(SongRepository songRepository,
                          MoodCalculator moodCalculator,
                          SetlistRepository setlistRepository) {
        this.songRepository = songRepository;
        this.moodCalculator = moodCalculator;
        this.setlistRepository = setlistRepository;
    }

    // -------------------- Songs --------------------

    @Transactional(readOnly = true)
    public List<Song> getAllSongs() {
        return songRepository.findAll();
    }

    // Backward‑compatible overload: delegates to the full signature with no mood/BPM preference.
    public List<Song> buildSetList(List<Song> candidates, int targetDurationSeconds) {
        return buildSetList(candidates, targetDurationSeconds, null, null);
    }

    @Transactional
    public Song createSong(Song song) {
        if (song == null) throw new IllegalArgumentException("song required");
        boolean hasTitle = song.getTitle() != null && !song.getTitle().trim().isEmpty();
        boolean hasArtist = song.getArtist() != null && !song.getArtist().trim().isEmpty();
        if (!hasTitle && !hasArtist) {
            throw new IllegalArgumentException("enter at least title or artist");
        }
        if (!song.isDurationValid()) throw new IllegalArgumentException("invalid duration");
        return songRepository.save(song);
    }

    // -------------------- Validation --------------------

    public boolean validateInput(String title, String artist) {
        boolean t = title != null && !title.trim().isEmpty();
        boolean a = artist != null && !artist.trim().isEmpty();
        return t || a;
    }

    // -------------------- Filtering --------------------

    @Transactional(readOnly = true)
    public List<Song> filterSongsByCriteria(String title,
                                            String artist,
                                            String rawGenre,
                                            Integer bpmIgnored,
                                            String rawMood) {
        List<Song> all = songRepository.findAll();
        String titleQ = normOrNull(title);
        String artistQ = normOrNull(artist);
        Set<SongGenre> allowedGenres = parseGenreOrGroup(rawGenre);
        SongMood mood = moodCalculator.parseMood(rawMood);

        return all.stream()
                .filter(s -> {
                    if (titleQ != null && (s.getTitle() == null || !containsIgnoreCase(s.getTitle(), titleQ))) return false;
                    if (artistQ != null && (s.getArtist() == null || !containsIgnoreCase(s.getArtist(), artistQ))) return false;
                    if (allowedGenres != null && (s.getGenre() == null || !allowedGenres.contains(s.getGenre()))) return false;
                    if (mood != null && s.getMood() != null && !s.getMood().compatibleWith(mood)) return false;
                    return true;
                })
                .collect(Collectors.toList());
    }

    // -------------------- Builder --------------------
    /**
     * Build a setlist from candidate songs attempting to match target duration,
     * desired mood and optional BPM. Returns a list of Song entities (not persisted).
     */
    public List<Song> buildSetList(List<Song> candidates,
                                   int targetDurationSeconds,
                                   String rawMood,
                                   Integer desiredBpm) {
        if (candidates == null || candidates.isEmpty() || targetDurationSeconds <= 0) return List.of();
        SongMood desiredMood = moodCalculator.parseMood(rawMood);

        // Score songs (simple heuristic). Consider migrating to MoodCalculator.score(...) for unified logic.
        Map<Long, Double> scoreById = new HashMap<>();
        for (Song s : candidates) {
            double score = 1.0;
            if (desiredMood != null && s.getMood() != null && s.getMood().compatibleWith(desiredMood)) {
                score += 1.0;
            }
            if (desiredBpm != null && s.getBpm() != null) {
                int diff = Math.abs(desiredBpm - s.getBpm());
                score += Math.max(0.0, 1.0 - (diff / 60.0));
            }
            scoreById.put(s.getId(), score);
        }

        List<Song> sorted = candidates.stream()
                .sorted((a, b) -> Double.compare(scoreById.getOrDefault(b.getId(), 0.0),
                        scoreById.getOrDefault(a.getId(), 0.0)))
                .toList();

        List<Song> result = new ArrayList<>();
        int total = 0;
        for (Song s : sorted) {
            int dur = s.getDurationInSeconds();
            if (dur <= 0) continue;
            if (total + dur > targetDurationSeconds) continue;
            result.add(s);
            total += dur;
            if (total >= targetDurationSeconds) break;
        }

        // Fallback: if none fit individually (e.g. each > target), pick the shortest.
        if (result.isEmpty()) {
            Song shortest = sorted.stream()
                    .filter(s -> s.getDurationInSeconds() > 0)
                    .min(Comparator.comparingInt(Song::getDurationInSeconds))
                    .orElse(null);
            if (shortest != null) result = List.of(shortest);
        }
        return result;
    }

    /**
     * Fill an existing set with reused songs (repeat songs) until target duration reached.
     * If allowOverflow is false, will avoid exceeding the target.
     */
    public List<Song> fillSetWithReusedSongs(List<Song> current,
                                             int targetDurationSeconds,
                                             boolean allowOverflow,
                                             String rawMood,
                                             Integer desiredBpm) {
        if (current == null || current.isEmpty() || targetDurationSeconds <= 0) return current;
        SongMood desiredMood = moodCalculator.parseMood(rawMood);

        // Score existing songs for reuse selection.
        Map<Song, Double> score = new HashMap<>();
        for (Song s : current) {
            double sc = 1.0;
            if (desiredMood != null && s.getMood() != null && s.getMood().compatibleWith(desiredMood)) sc += 0.5;
            if (desiredBpm != null && s.getBpm() != null) {
                int diff = Math.abs(desiredBpm - s.getBpm());
                sc += Math.max(0.0, 0.5 - (diff / 100.0));
            }
            score.put(s, sc);
        }

        List<Song> ranked = current.stream()
                .sorted((a, b) -> Double.compare(score.getOrDefault(b, 0.0), score.getOrDefault(a, 0.0)))
                .toList();

        List<Song> out = new ArrayList<>(current);
        int total = out.stream().mapToInt(Song::getDurationInSeconds).sum();
        int idx = 0;
        while (total < targetDurationSeconds && !ranked.isEmpty()) {
            Song pick = ranked.get(idx % ranked.size());
            out.add(pick);
            total += pick.getDurationInSeconds();
            idx++;
            if (!allowOverflow && total > targetDurationSeconds) {
                out.remove(out.size() - 1);
                break;
            }
        }
        return out;
    }

    // -------------------- Persistence --------------------

    /**
     * Persist a built setlist (list of Song entities) into the database.
     * Uses Setlist.addItem(...) to keep bidirectional relationships consistent.
     */
    @Transactional
    public void saveBuiltSetlist(Profile owner, String title, List<Song> songs) {
        if (songs == null) throw new IllegalArgumentException("songs required");
        String t = (title == null || title.isBlank()) ? "Setlist" : title.trim();

        Setlist entity = new Setlist();
        entity.setOwner(owner); // may be null (guest)
        entity.setTitle(t);
        // createdAt is handled by Setlist.onCreate() @PrePersist — avoid setting it here.

        int idx = 0;
        int totalSeconds = 0;
        for (Song s : songs) {
            if (s == null) continue;
            SetlistItem item = new SetlistItem();
            item.setPositionIndex(idx++);
            item.setSong(s);
            item.setReused(false);
            entity.addItem(item); // keeps both sides in sync
            totalSeconds += s.getDurationInSeconds();
        }
        entity.setTotalDurationSeconds(totalSeconds);
        setlistRepository.save(entity);
    }

    /**
     * Convenience variant that resolves the owner from the current HTTP session.
     */
    @Transactional
    public void saveBuiltSetlist(String title, List<Song> songs) {
        Profile owner = resolveSessionProfile();
        saveBuiltSetlist(owner, title, songs);
    }

    /**
     * Persist a manual setlist created by the user from an ordered list of Song ids.
     * - Preserves the provided order.
     * - Ignores ids that are not found.
     * - Saves resulting Setlist (owner may be null).
     */
    @Transactional
    public void saveManualSetlist(Profile owner, String title, List<Long> songIds) {
        if (songIds == null) throw new IllegalArgumentException("songIds required");
        if (songIds.isEmpty()) throw new IllegalArgumentException("songIds cannot be empty");

        // Fetch all songs present and preserve order based on incoming ids.
        List<Song> fetched = songRepository.findAllById(songIds);
        Map<Long, Song> byId = fetched.stream().filter(Objects::nonNull)
                .collect(Collectors.toMap(Song::getId, s -> s));

        List<Song> ordered = songIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (ordered.isEmpty()) throw new IllegalArgumentException("no valid songs found for provided ids");

        saveBuiltSetlist(owner, title, ordered);
    }

    // -------------------- Listing --------------------

    @Transactional(readOnly = true)
    public List<SetlistSummaryDto> listSetlists() {
        return setlistRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(SetlistSummaryDto::from)
                .toList();
    }

    // -------------------- Session helper --------------------

    private Profile resolveSessionProfile() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        HttpServletRequest req = attrs.getRequest();
        if (req == null) return null;
        HttpSession session = req.getSession(false);
        if (session == null) return null;
        Object obj = session.getAttribute("profile");
        return (obj instanceof Profile) ? (Profile) obj : null;
    }

    // -------------------- Internal helpers --------------------

    private static String normOrNull(String s) {
        if (s == null) return null;
        String n = s.trim();
        return n.isEmpty() ? null : n.toLowerCase(Locale.ROOT);
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null) return false;
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static Set<SongGenre> parseGenre(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            SongGenre g = SongGenre.valueOf(raw.trim().replaceAll("[\\s\\-]+", "_").toUpperCase(Locale.ROOT));
            return EnumSet.of(g);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Set<SongGenre> parseGenreOrGroup(String raw) {
        Set<SongGenre> group = SongGenreGroup.resolve(raw);
        if (group != null) return group;
        return parseGenre(raw);
    }

    // -------------------- DTO persistence helpers --------------------
    @Transactional
    public void saveBuiltSetlistFromDtos(Profile owner, String title, List<dk.ek.setlistgpt.song.SongDto> dtos) {
        if (dtos == null) throw new IllegalArgumentException("songs required");
        List<Song> songs = new ArrayList<>(dtos.size());
        for (dk.ek.setlistgpt.song.SongDto dto : dtos) {
            if (dto == null) continue;
            Song song = null;
            if (dto.getId() != null) {
                song = songRepository.findById(dto.getId()).orElse(null);
            }
            if (song == null) {
                song = new Song();
                song.setTitle(dto.getTitle());
                song.setArtist(dto.getArtist());
                song.setGenre(dto.getGenre());
                song.setBpm(dto.getBpm());
                song.setMood(dto.getMood());
                song.setDurationMinutes(dto.getDurationMinutes());
                song.setDurationSeconds(dto.getDurationSeconds());
                song = songRepository.save(song);
            }
            songs.add(song);
        }
        saveBuiltSetlist(owner, title, songs);
    }
}