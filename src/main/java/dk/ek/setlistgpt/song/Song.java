package dk.ek.setlistgpt.song;

import dk.ek.setlistgpt.repertoire.Repertoire;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

/**
 * Represents a song with various attributes such as title, artist, genre, BPM, mood, and duration.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "songs")
public class Song {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title; // nullable but enter either title or artist
    private String artist; // nullable but enter either title or artist

    @Enumerated(EnumType.STRING)
    private SongGenre genre; // nullable
    private Integer bpm; // nullable

    @Enumerated(EnumType.STRING)
    private SongMood mood; // nullable

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repertoire_id")
    @JsonBackReference
    private Repertoire repertoire;

    // Duration expressed as minutes (0-59).
    @Builder.Default
    private int durationMinutes = 0;

    // Duration expressed as seconds (0-59).
    @Builder.Default
    private int durationSeconds = 0;

    //Convenience setter that validates and sets minutes and seconds.
    //@throws IllegalArgumentException if minutes or seconds out of range or total duration is 0.
    public void setDuration(int minutes, int seconds) {
        if (minutes < 0 || minutes > 59) {
            throw new IllegalArgumentException("Minutes must be between 0 and 59");
        }
        if (seconds < 0 || seconds > 59) {
            throw new IllegalArgumentException("Seconds must be between 0 and 59");
        }
        if (minutes == 0 && seconds == 0) {
            throw new IllegalArgumentException("Duration must be at least 00:01");
        }
        this.durationMinutes = minutes;
        this.durationSeconds = seconds;
    }

    // Returns total duration in seconds.
    public int getDurationInSeconds() {
        return durationMinutes * 60 + durationSeconds;
    }

    // Checks whether the duration is within the allowed range 00:01..59:59.
    public boolean isDurationValid() {
        int total = getDurationInSeconds();
        return total >= 1 && total <= (59 * 60 + 59);
    }

    @PrePersist
    @PreUpdate
    private void validateDurationOnPersist() {
        if (!isDurationValid()) {
            throw new IllegalStateException("Song duration must be within 00:01..59:59");
        }
    }
}