package dk.ek.setlistgpt.profile;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "profiles", indexes = {
        @Index(name = "ux_profiles_name", columnList = "name", unique = true)
})
public class Profile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // unique username

    // Write-only: accept in requests, omit from responses
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(nullable = false)
    private String password; // TODO: hash in production

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ProfileType type = ProfileType.MUSICIAN;
}
