// src/main/java/dk/ek/setlistgpt/setlist/Setlist.java
package dk.ek.setlistgpt.setlist;

import dk.ek.setlistgpt.profile.Profile;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a persisted setlist built from songs.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "setlists")
public class Setlist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    // Switched from Instant to LocalDateTime (user request).
    private LocalDateTime createdAt;

    private int totalDurationSeconds;

    // Owner (nullable for backward compatibility with older rows).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    @JsonBackReference
    private Profile owner;

    @OneToMany(mappedBy = "setlist", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<SetlistItem> items = new ArrayList<>();

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public void addItem(SetlistItem item) {
        if (item == null) return;
        items.add(item);
        item.setSetlist(this);
    }
}