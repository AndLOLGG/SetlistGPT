package dk.ek.setlistgpt.song;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SongDto {
    private Long id;
    private String title;
    private String artist;
    private SongGenre genre;
    private Integer bpm;
    private SongMood mood;
    private int durationMinutes;
    private int durationSeconds;
    private int durationInSeconds;

    public static SongDto from(Song s) {
        if (s == null) return null;
        return new SongDto(
                s.getId(),
                s.getTitle(),
                s.getArtist(),
                s.getGenre(),
                s.getBpm(),
                s.getMood(),
                s.getDurationMinutes(),
                s.getDurationSeconds(),
                s.getDurationInSeconds()
        );
    }
}
