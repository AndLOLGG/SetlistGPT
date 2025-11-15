package dk.ek.setlistgpt.setlist;

import dk.ek.setlistgpt.song.Song;
import dk.ek.setlistgpt.song.SongMood;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Small helper that computes how well a Song matches a requested mood and optional BPM.
 * Returns score in [0,1]. Exact mood match => 1.0, related => 0.8, otherwise low.
 * BPM closeness is scored 0..1 (1.0 = exact BPM). Final score is a weighted combination.
 */
@Service
public class MoodCalculator {

    private static final Map<SongMood, Set<SongMood>> RELATED = new EnumMap<>(SongMood.class);

    static {
        putGroup(SongMood.ENERGETIC, SongMood.DRIVING, SongMood.PARTY, SongMood.GROOVY);
        putGroup(SongMood.MELLOW, SongMood.CALM, SongMood.DREAMY, SongMood.NOSTALGIC);
        putGroup(SongMood.HAPPY, SongMood.UPLIFTING, SongMood.PARTY, SongMood.DRIVING);
        putGroup(SongMood.SAD, SongMood.MELANCHOLIC, SongMood.DARK);
        putGroup(SongMood.INTENSE, SongMood.ANGRY, SongMood.DRIVING);
        putGroup(SongMood.ROMANTIC, SongMood.MELLOW, SongMood.DREAMY, SongMood.NOSTALGIC, SongMood.UPLIFTING);
        putGroup(SongMood.CHILL, SongMood.CALM, SongMood.MELLOW, SongMood.DREAMY);
        putGroup(SongMood.UPLIFTING, SongMood.HAPPY, SongMood.PARTY, SongMood.GROOVY);
        putGroup(SongMood.DARK, SongMood.SAD, SongMood.INTENSE, SongMood.MELANCHOLIC);
        putGroup(SongMood.PARTY, SongMood.ENERGETIC, SongMood.UPLIFTING, SongMood.GROOVY);
        putGroup(SongMood.DRIVING, SongMood.ENERGETIC, SongMood.INTENSE, SongMood.GROOVY);
        putGroup(SongMood.GROOVY, SongMood.PARTY, SongMood.ENERGETIC, SongMood.DRIVING);
        putGroup(SongMood.MELANCHOLIC, SongMood.SAD, SongMood.DARK, SongMood.NOSTALGIC);
        putGroup(SongMood.DREAMY, SongMood.MELLOW, SongMood.ROMANTIC, SongMood.CHILL);
        putGroup(SongMood.NOSTALGIC, SongMood.MELLOW, SongMood.DREAMY, SongMood.MELANCHOLIC);
        // Added explicit groups:
        putGroup(SongMood.CALM, SongMood.CHILL, SongMood.MELLOW, SongMood.DREAMY);
        putGroup(SongMood.ANGRY, SongMood.INTENSE, SongMood.DRIVING, SongMood.DARK);
    }

    private static void putGroup(SongMood key, SongMood... others) {
        RELATED.computeIfAbsent(key, k -> new HashSet<>()).addAll(Arrays.asList(others));
        // make relations symmetric: add key to each other's set
        for (SongMood o : others) {
            RELATED.computeIfAbsent(o, k -> new HashSet<>()).add(key);
        }
    }

    public SongMood parseMood(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String norm = raw.trim().replaceAll("[\\s\\-]+", "_").toUpperCase();
        for (SongMood m : SongMood.values()) {
            if (m.name().equals(norm)) return m;
        }
        return null;
    }

    /**
     * Compute a combined mood+bpm compatibility score in [0,1].
     *
     * @param song       song to score
     * @param desired    desired SongMood (nullable)
     * @param desiredBpm desired bpm (nullable)
     * @return combined score
     */
    public double score(Song song, SongMood desired, Integer desiredBpm) {
        // BPM scoring helper: linear falloff with maxDiff 60, minimum 0.1
        double bpmScore = 0.5;
        Integer sBpm = song.getBpm();
        if (desiredBpm == null) {
            bpmScore = 0.5; // neutral if user didn't request bpm
        } else if (sBpm == null) {
            bpmScore = 0.2; // low if song lacks bpm
        } else {
            int diff = Math.abs(sBpm - desiredBpm);
            double raw = 1.0 - ((double) diff / 60.0); // diff 0 -> 1.0, diff 60 -> 0.0
            bpmScore = Math.max(0.1, raw); // clamp to minimum
        }

        // Mood scoring
        double moodScore;
        if (desired == null) {
            moodScore = 0.5; // neutral when no desired mood
        } else {
            SongMood sMood = song.getMood();
            if (sMood == null) {
                moodScore = 0.2;
            } else if (sMood == desired) {
                moodScore = 1.0;
            } else {
                Set<SongMood> rel = RELATED.getOrDefault(desired, Collections.emptySet());
                moodScore = rel.contains(sMood) ? 0.8 : 0.1;
            }
        }

        // Combine with weights: mood 70%, bpm 30%. If only one aspect is present, it still influences.
        double moodWeight = 0.7;
        double bpmWeight = 0.3;

        // If desired mood is missing, prefer bpm only; if bpm missing, prefer mood only (weights still apply).
        if (desired == null && desiredBpm != null) {
            return bpmScore;
        } else if (desiredBpm == null && desired != null) {
            return moodScore;
        } else if (desired == null && desiredBpm == null) {
            return 0.5;
        }

        return Math.min(1.0, Math.max(0.0, moodWeight * moodScore + bpmWeight * bpmScore));
    }
}