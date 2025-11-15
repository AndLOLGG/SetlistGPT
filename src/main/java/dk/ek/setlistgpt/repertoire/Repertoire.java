package dk.ek.setlistgpt.repertoire;

import com.fasterxml.jackson.annotation.JsonBackReference;
import dk.ek.setlistgpt.profile.Profile;
import dk.ek.setlistgpt.song.Song;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "repertoires")
public class Repertoire {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepertoireVisibility visibility = RepertoireVisibility.PRIVATE;

    // Owner (nullable to keep backward compatibility with existing data)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    @JsonBackReference
    private Profile owner;

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
}