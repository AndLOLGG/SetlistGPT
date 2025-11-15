package dk.ek.setlistgpt.setlist;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import dk.ek.setlistgpt.song.Song;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "setlist_items")
public class SetlistItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int positionIndex;

    private boolean reused;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonBackReference
    private Setlist setlist;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "song_id")
    private Song song;

    @JsonIgnore
    public int getDurationInSeconds() {
        return song != null ? song.getDurationInSeconds() : 0;
    }
}
