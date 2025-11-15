package dk.ek.setlistgpt.admin;

import dk.ek.setlistgpt.repertoire.Repertoire;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminRepertoireSummaryDto {
    private Long id;
    private String title;
    private int songCount;

    public static AdminRepertoireSummaryDto from(Repertoire r) {
        int cnt = (r.getSongs() == null) ? 0 : r.getSongs().size();
        return new AdminRepertoireSummaryDto(r.getId(), r.getTitle(), cnt);
    }
}
