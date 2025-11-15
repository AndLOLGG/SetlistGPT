package dk.ek.setlistgpt.admin;

import dk.ek.setlistgpt.song.Song;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminSongListItemDto {
    private Long id;
    private String title;
    private String artist;
    private int durationInSeconds;

    public static AdminSongListItemDto from(Song s) {
        return new AdminSongListItemDto(
                s.getId(),
                s.getTitle(),
                s.getArtist(),
                s.getDurationInSeconds()
        );
    }
}
