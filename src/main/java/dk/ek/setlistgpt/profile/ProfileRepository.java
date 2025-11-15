package dk.ek.setlistgpt.profile;

import dk.ek.setlistgpt.admin.AdminProfileSummaryDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/** Spring Data JPA repository for Profile (added summary projection query; existing methods unchanged). */
public interface ProfileRepository extends JpaRepository<Profile, Long> {
    Optional<Profile> findByName(String name);
    boolean existsByName(String name);

    @Query("""
           select new dk.ek.setlistgpt.admin.AdminProfileSummaryDto(
               p.id,
               p.name,
               p.type,
               (select count(r) from Repertoire r where r.owner = p),
               (select count(s) from Song s join s.repertoire rep where rep.owner = p),
               (select count(sl) from Setlist sl where sl.owner = p)
           )
           from Profile p
           """)
    List<AdminProfileSummaryDto> fetchAdminProfileSummaries();
}