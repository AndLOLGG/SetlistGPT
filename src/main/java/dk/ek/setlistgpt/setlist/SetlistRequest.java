package dk.ek.setlistgpt.setlist;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO used by `SetlistController`.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SetlistRequest {
    private String title;
    private String artist;
    private String genre;
    private Integer bpm;
    private String mood;
    private int durationMinutes;
    private int durationSeconds;
    private boolean allowReuse;
}