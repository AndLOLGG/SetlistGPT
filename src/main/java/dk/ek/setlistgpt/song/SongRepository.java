package dk.ek.setlistgpt.song;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for Song */
public interface SongRepository extends JpaRepository<Song, Long> {
}
