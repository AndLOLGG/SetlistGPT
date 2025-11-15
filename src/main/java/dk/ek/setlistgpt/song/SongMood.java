package dk.ek.setlistgpt.song;

import java.util.*;

/**
 * Enum representing various moods a song can have, plus compatibility logic.
 */
public enum SongMood {
    HAPPY,
    SAD,
    ENERGETIC,
    CALM,
    ANGRY,
    ROMANTIC,
    MELANCHOLIC,
    UPLIFTING,
    DARK,
    CHILL,
    PARTY,
    DRIVING,
    MELLOW,
    INTENSE,
    DREAMY,
    NOSTALGIC,
    GROOVY;

    private static final Map<SongMood, Set<SongMood>> RELATED = new EnumMap<>(SongMood.class);

    static {
        group(ENERGETIC, DRIVING, PARTY, GROOVY);
        group(MELLOW, CALM, DREAMY, NOSTALGIC);
        group(HAPPY, UPLIFTING, PARTY, DRIVING);
        group(SAD, MELANCHOLIC, DARK);
        group(INTENSE, ANGRY, DRIVING);
        group(ROMANTIC, MELLOW, DREAMY, NOSTALGIC, UPLIFTING);
        group(CHILL, CALM, MELLOW, DREAMY);
        group(UPLIFTING, HAPPY, PARTY, GROOVY);
        group(DARK, SAD, INTENSE, MELANCHOLIC);
        group(PARTY, ENERGETIC, UPLIFTING, GROOVY);
        group(DRIVING, ENERGETIC, INTENSE, GROOVY);
        group(GROOVY, PARTY, ENERGETIC, DRIVING);
        group(MELANCHOLIC, SAD, DARK, NOSTALGIC);
        group(DREAMY, MELLOW, ROMANTIC, CHILL);
        group(NOSTALGIC, MELLOW, DREAMY, MELANCHOLIC);
        group(CALM, CHILL, MELLOW, DREAMY);
        group(ANGRY, INTENSE, DRIVING, DARK);
    }

    private static void group(SongMood key, SongMood... others) {
        RELATED.computeIfAbsent(key, k -> new HashSet<>()).addAll(Arrays.asList(others));
        for (SongMood o : others) {
            RELATED.computeIfAbsent(o, k -> new HashSet<>()).add(key);
        }
    }

    /**
     * Returns true if this mood is the same as or related to the other mood.
     */
    public boolean compatibleWith(SongMood other) {
        if (other == null) return false;
        if (this == other) return true;
        return RELATED.getOrDefault(this, Collections.emptySet()).contains(other);
    }
}