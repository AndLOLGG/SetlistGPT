package dk.ek.setlistgpt.repertoire;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import dk.ek.setlistgpt.profile.Profile;
import dk.ek.setlistgpt.song.Song;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "repertoires")
public class Repertoire {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepertoireVisibility visibility = RepertoireVisibility.PRIVATE;

    // Owner (nullable to keep backward compatibility with existing data)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    @JsonBackReference
    private Profile owner;

    // denormalized owner name for public API grouping / faster queries
    @Column(name = "owner_name")
    private String ownerName;

    @OneToMany(mappedBy = "repertoire", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<Song> songs = new ArrayList<>();

    public void addSong(Song song) {
        songs.add(song);
        song.setRepertoire(this);
    }

    public void removeSong(Song song) {
        songs.remove(song);
        song.setRepertoire(null);
    }

    // Ensure ownerName is kept in sync with owner.name when saving/updating
    @PrePersist
    @PreUpdate
    private void syncOwnerName() {
        if (this.owner != null && this.owner.getName() != null) {
            this.ownerName = this.owner.getName();
        } else if (this.owner == null) {
            // Optionally clear ownerName when owner removed; keep consistent with desired semantics
            this.ownerName = this.ownerName; // no-op (preserve) or set to null: this.ownerName = null;
        }
    }

    // Provide a `title` JSON property expected by the frontend (maps to `name`).
    @JsonProperty("title")
    public String getTitle() {
        return this.name;
    }
}