package dk.ek.setlistgpt.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Existing grouped admin/musician profiles with optional system summary counts added
 * (new fields are additive; remove them if not needed).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminProfilesGroupedDto {
    private List<AdminProfileSummaryDto> admins;
    private List<AdminProfileSummaryDto> musicians;

    // Added: optional high-level counts (populate only if endpoint wants them)
    private Long totalProfiles;
    private Long adminProfiles;
    private Long musicianProfiles;
    private Long repertoires;
    private Long songs;
    private Long setlists;
    private String currentAdminName;

    // Added: convenience ctor matching original usage (admins + musicians only).
    public AdminProfilesGroupedDto(List<AdminProfileSummaryDto> admins,
                                   List<AdminProfileSummaryDto> musicians) {
        this.admins = admins;
        this.musicians = musicians;
    }
}
