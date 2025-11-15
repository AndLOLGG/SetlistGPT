package dk.ek.setlistgpt.setlist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data JPA repository for Setlist */
public interface SetlistRepository extends JpaRepository<Setlist, Long> {
    List<Setlist> findAllByOrderByCreatedAtDesc();

    // Admin helpers
    List<Setlist> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    long countByOwnerId(Long ownerId);
}
