package dk.ek.setlistgpt.repertoire;

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