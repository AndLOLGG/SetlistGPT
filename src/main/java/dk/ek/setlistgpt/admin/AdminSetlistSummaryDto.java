package dk.ek.setlistgpt.admin;

import dk.ek.setlistgpt.setlist.Setlist;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminSetlistSummaryDto {
    private Long id;
    private String title;
    private int songCount;

    public static AdminSetlistSummaryDto from(Setlist s) {
        int cnt = (s.getItems() == null) ? 0 : s.getItems().size();
        return new AdminSetlistSummaryDto(s.getId(), s.getTitle(), cnt);
    }
}
