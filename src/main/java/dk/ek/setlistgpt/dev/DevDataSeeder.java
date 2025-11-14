package dk.ek.setlistgpt.dev;

import dk.ek.setlistgpt.repertoire.Repertoire;
import dk.ek.setlistgpt.repertoire.RepertoireRepository;
import dk.ek.setlistgpt.repertoire.RepertoireVisibility;
import dk.ek.setlistgpt.repertoire.RepertoireService;
import dk.ek.setlistgpt.setlist.SetlistService;
import dk.ek.setlistgpt.song.Song;
import dk.ek.setlistgpt.song.SongGenre;
import dk.ek.setlistgpt.song.SongMood;
import dk.ek.setlistgpt.song.SongRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/** Seeds development data into the database on application startup. */
@Component
public class DevDataSeeder {
    private final SongRepository songRepo;
    private final RepertoireRepository repRepo;
    private final RepertoireService repService;
    private final SetlistService setlistService;
    private final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    public DevDataSeeder(SongRepository songRepo,
                         RepertoireRepository repRepo,
                         RepertoireService repService,
                         SetlistService setlistService) {
        this.songRepo = songRepo;
        this.repRepo = repRepo;
        this.repService = repService;
        this.setlistService = setlistService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        try {
            if (songRepo.count() > 0) {
                log.info("DevDataSeeder: data already present, skipping seeding.");
                return;
            }

            Song s1 = Song.builder()
                    .title("Sunrise Drive")
                    .artist("The Demo Band")
                    .genre(SongGenre.ROCK)
                    .bpm(120)
                    .mood(SongMood.ENERGETIC)
                    .build();
            s1.setDuration(3, 30);

            Song s2 = Song.builder()
                    .title("Midnight Ballad")
                    .artist("Demo Singer")
                    .genre(SongGenre.POP)
                    .bpm(80)
                    .mood(SongMood.MELLOW)
                    .build();
            s2.setDuration(4, 10);

            Song s3 = Song.builder()
                    .title("Quick Jam")
                    .artist("The Demo Band")
                    .genre(SongGenre.FUNK)
                    .bpm(130)
                    .mood(SongMood.GROOVY)
                    .build();
            s3.setDuration(2, 20);

            List<Song> saved = songRepo.saveAll(List.of(s1, s2, s3));

            // Fetch within the same transaction/persistence context so entities are managed
            Song managed1 = songRepo.findById(saved.get(0).getId()).orElseThrow();
            Song managed2 = songRepo.findById(saved.get(1).getId()).orElseThrow();

            Repertoire rep = new Repertoire();
            rep.setName("Demo Repertoire");
            rep.setVisibility(RepertoireVisibility.PUBLIC);
            rep.addSong(managed1);
            rep.addSong(managed2);
            repRepo.save(rep);

            List<Song> allSongs = songRepo.findAll();
            int maxDurationSeconds = 10 * 60;
            List<Song> setlist = setlistService.buildSetList(allSongs, maxDurationSeconds);

            String titles = setlist.stream().map(Song::getTitle).collect(Collectors.joining(", "));
            log.info("DevDataSeeder: seeded {} songs and repertoire '{}'. Built setlist ({}s): {}",
                    allSongs.size(), rep.getName(),
                    setlist.stream().mapToInt(Song::getDurationInSeconds).sum(),
                    titles);
        } catch (Exception e) {
            log.error("DevDataSeeder: seeding failed (non-fatal).", e);
        }
    }
}