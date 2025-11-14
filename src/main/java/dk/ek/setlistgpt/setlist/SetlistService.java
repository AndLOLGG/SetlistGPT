package dk.ek.setlistgpt.setlist;

import dk.ek.setlistgpt.song.Song;
import dk.ek.setlistgpt.song.SongRepository;
import dk.ek.setlistgpt.song.SongMood;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service class for managing songs and building set lists based on criteria.
 * Mood+BPM weighting is optional; pass null/blank for desiredMood and/or null for desiredBpm to disable parts.
 */
@Service
public class SetlistService {

    private final SongRepository songRepository;
    private final MoodCalculator moodCalculator;

    public SetlistService(SongRepository songRepository, MoodCalculator moodCalculator) {
        this.songRepository = songRepository;
        this.moodCalculator = moodCalculator;
    }

    @Transactional(readOnly = true)
    public List<Song> getAllSongs() {
        return songRepository.findAll();
    }

    @Transactional
    public Song createSong(Song song) {
        return songRepository.save(song);
    }

    public boolean validateInput(String title, String artist) {
        return !((title == null || title.isBlank()) && (artist == null || artist.isBlank()));
    }

    public List<Song> filterSongsByCriteria(String title, String artist, String genre, Integer bpm, String mood) {
        return getAllSongs().stream()
                .filter(s -> title == null || title.isBlank() || (s.getTitle() != null && s.getTitle().equalsIgnoreCase(title)))
                .filter(s -> artist == null || artist.isBlank() || (s.getArtist() != null && s.getArtist().equalsIgnoreCase(artist)))
                .filter(s -> genre == null || genre.isBlank() || (s.getGenre() != null && s.getGenre().name().equalsIgnoreCase(genre)))
                .filter(s -> bpm == null || Objects.equals(s.getBpm(), bpm))
                // only filter by explicit mood when provided
                .filter(s -> mood == null || mood.isBlank() || (s.getMood() != null && s.getMood().name().equalsIgnoreCase(mood)))
                .collect(Collectors.toList());
    }

    /**
     * Builds a set list; mood+bpm weighting is optional.
     *
     * @param filtered    list after filtering
     * @param maxDuration max seconds
     * @param desiredMood optional mood string; may be null
     * @param desiredBpm  optional desired bpm; may be null
     * @return built setlist
     */
    public List<Song> buildSetList(List<Song> filtered, int maxDuration, String desiredMood, Integer desiredBpm) {
        if (filtered == null) return Collections.emptyList();
        SongMood target = moodCalculator.parseMood(desiredMood);

        List<Song> candidates = new ArrayList<>(filtered);

        if (target != null || desiredBpm != null) {
            candidates.sort(Comparator.<Song, Double>comparing(s -> moodCalculator.score(s, target, desiredBpm)).reversed()
                    .thenComparingInt(Song::getDurationInSeconds));
        } else {
            // no weighting -> prefer shorter songs to maximize fit
            candidates.sort(Comparator.comparingInt(Song::getDurationInSeconds));
        }

        List<Song> setList = new ArrayList<>();
        int totalDuration = 0;
        for (Song song : candidates) {
            if (!song.isDurationValid()) continue;
            if (totalDuration + song.getDurationInSeconds() <= maxDuration) {
                setList.add(song);
                totalDuration += song.getDurationInSeconds();
            }
        }
        return setList;
    }

    // Backwards-compatible overloads
    public List<Song> buildSetList(List<Song> filtered, int maxDuration, String desiredMood) {
        return buildSetList(filtered, maxDuration, desiredMood, null);
    }

    public List<Song> buildSetList(List<Song> filtered, int maxDuration) {
        return buildSetList(filtered, maxDuration, null, null);
    }

    /**
     * Fill set with reused songs, optional mood+bpm weighting similar to buildSetList.
     */
    public List<Song> fillSetWithReusedSongs(List<Song> currentSet, int maxDuration, boolean allowReuse, String desiredMood, Integer desiredBpm) {
        int totalDuration = currentSet.stream().mapToInt(Song::getDurationInSeconds).sum();

        List<Song> pool = allowReuse ? new ArrayList<>(getAllSongs())
                : getAllSongs().stream().filter(s -> !currentSet.contains(s)).collect(Collectors.toList());

        SongMood target = moodCalculator.parseMood(desiredMood);

        if (target != null || desiredBpm != null) {
            pool.sort(Comparator.<Song, Double>comparing(s -> moodCalculator.score(s, target, desiredBpm)).reversed()
                    .thenComparingInt(Song::getDurationInSeconds));
        } else {
            pool.sort(Comparator.comparingInt(Song::getDurationInSeconds));
        }

        for (Song song : pool) {
            if (!song.isDurationValid()) continue;
            if (totalDuration + song.getDurationInSeconds() <= maxDuration) {
                currentSet.add(song);
                totalDuration += song.getDurationInSeconds();
            }
        }
        return currentSet;
    }

    // Backwards-compatible overloads
    public List<Song> fillSetWithReusedSongs(List<Song> currentSet, int maxDuration, boolean allowReuse) {
        return fillSetWithReusedSongs(currentSet, maxDuration, allowReuse, null, null);
    }

    public List<Song> fillSetWithReusedSongs(List<Song> currentSet, int maxDuration, boolean allowReuse, String desiredMood) {
        return fillSetWithReusedSongs(currentSet, maxDuration, allowReuse, desiredMood, null);
    }
}