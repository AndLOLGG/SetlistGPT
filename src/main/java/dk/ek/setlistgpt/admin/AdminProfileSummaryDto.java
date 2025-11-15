package dk.ek.setlistgpt.admin;

import dk.ek.setlistgpt.profile.ProfileType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminProfileSummaryDto {
    private Long id;
    private String name;
    private ProfileType type;
    private long repertoireCount;
    private long songCount;
    private long setlistCount;
}
