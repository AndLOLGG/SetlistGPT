package dk.ek.setlistgpt.setlist;

import dk.ek.setlistgpt.repertoire.Repertoire;
import dk.ek.setlistgpt.repertoire.RepertoireVisibility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data JPA repository for SetlistRepository */
public interface SetlistRepository extends JpaRepository<Repertoire, Long> {
    List<Repertoire> findByVisibility(RepertoireVisibility visibility);
}
