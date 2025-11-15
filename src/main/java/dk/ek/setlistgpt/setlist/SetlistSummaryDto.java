package dk.ek.setlistgpt.setlist;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Lightweight summary projection for listing setlists.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SetlistSummaryDto {
    private Long id;
    private String title;
    private LocalDateTime createdAt;
    private int totalDurationSeconds;
    private int items;

    public static SetlistSummaryDto from(Setlist s) {
        return new SetlistSummaryDto(
                s.getId(),
                s.getTitle(),
                s.getCreatedAt(),
                s.getTotalDurationSeconds(),
                s.getItems() == null ? 0 : s.getItems().size()
        );
    }
}