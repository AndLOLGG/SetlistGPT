package dk.ek.setlistgpt.repertoire;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data JPA repository for Repertoire */
public interface RepertoireRepository extends JpaRepository<Repertoire, Long> {
    List<Repertoire> findByVisibility(RepertoireVisibility visibility);
}