// SongGenreGroup.java
package dk.ek.setlistgpt.song;

import java.util.*;

public enum SongGenreGroup {
    POP_GROUP(SongGenre.POP, SongGenre.RNB, SongGenre.ALTERNATIVE, SongGenre.ROCK),
    ROCK_GROUP(SongGenre.ROCK, SongGenre.HARD_ROCK, SongGenre.INDIE, SongGenre.ALTERNATIVE, SongGenre.POP),
    HARD_ROCK_GROUP(SongGenre.HARD_ROCK, SongGenre.METAL, SongGenre.HEAVY_METAL, SongGenre.PUNK, SongGenre.ALTERNATIVE, SongGenre.ROCK),
    METAL_GROUP(SongGenre.METAL, SongGenre.HEAVY_METAL, SongGenre.PUNK, SongGenre.HARD_ROCK),
    ELECTRONIC_GROUP(SongGenre.ELECTRONIC, SongGenre.DANCE, SongGenre.HOUSE, SongGenre.TRANCE, SongGenre.TECHNO),
    URBAN_GROUP(SongGenre.HIP_HOP, SongGenre.RAP, SongGenre.RNB, SongGenre.SOUL, SongGenre.FUNK),
    ACOUSTIC_GROUP(SongGenre.FOLK, SongGenre.COUNTRY, SongGenre.BLUES, SongGenre.JAZZ),
    LATIN_WORLD_GROUP(SongGenre.LATIN, SongGenre.REGGAE, SongGenre.WORLD);

    private final Set<SongGenre> members;

    SongGenreGroup(SongGenre... genres) {
        this.members = Collections.unmodifiableSet(EnumSet.copyOf(Arrays.asList(genres)));
    }

    public Set<SongGenre> members() {
        return members;
    }

    public static Set<SongGenre> resolve(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String norm = raw.trim().replaceAll("[\\s\\-]+", "_").toUpperCase(Locale.ROOT);

        // Try group
        for (SongGenreGroup g : values()) {
            if (g.name().equals(norm)) return g.members();
        }
        // Try single genre
        try {
            SongGenre single = SongGenre.valueOf(norm);
            return EnumSet.of(single);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
