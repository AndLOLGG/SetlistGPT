package dk.ek.setlistgpt.repertoire;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data JPA repository for Repertoire */
public interface RepertoireRepository extends JpaRepository<Repertoire, Long> {

    @EntityGraph(attributePaths = {"owner", "songs"})
    List<Repertoire> findByVisibility(RepertoireVisibility visibility);

    // Admin helpers - fetch owner and songs so controller can sort without lazy issues
    @EntityGraph(attributePaths = {"owner", "songs"})
    List<Repertoire> findByOwnerId(Long ownerId);

    long countByOwnerId(Long ownerId);
}