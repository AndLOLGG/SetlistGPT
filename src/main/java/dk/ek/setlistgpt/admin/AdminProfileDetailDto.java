package dk.ek.setlistgpt.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminProfileDetailDto {
    private Long id;
    private String name;
    private List<AdminRepertoireSummaryDto> repertoires;
    private List<AdminSetlistSummaryDto> setlists;
}
