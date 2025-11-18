package dk.ek.setlistgpt.groq;

import dk.ek.setlistgpt.song.SongDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Model representing the structured inputs the AI should use to build a setlist.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SetlistAiPrompt {
    private List<SongDto> repertoire;
    private String desiredMood;          // nullable
    private Integer desiredBpm;          // nullable
    private String genreFilter;          // nullable (single genre or group)
    private int targetDurationSeconds;   // required (>0)
    private boolean allowReuse;
    private boolean allowOverflow;

    // Optional context for API validation / persistence
    private String title;   // nullable — controller requires title OR artist
    private String artist;  // nullable
}