package dk.ek.setlistgpt.repertoire;

import dk.ek.setlistgpt.song.Song;
import dk.ek.setlistgpt.song.SongRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Service for managing Repertoires and their Songs */
@Service
public class RepertoireService {

    private final RepertoireRepository repertoireRepository;
    private final SongRepository songRepository;

    public RepertoireService(RepertoireRepository repertoireRepository, SongRepository songRepository) {
        this.repertoireRepository = repertoireRepository;
        this.songRepository = songRepository;
    }

    // Add an existing Song to a Repertoire using entity helpers (keeps both sides in sync).
    // Returns the Song if successful or null if repertoire/song not found.
    // Throws IllegalStateException if Song belongs to a different repertoire.
    @Transactional
    public Song addExistingSongToRepertoire(Long repertoireId, Long songId) {
        Optional<Repertoire> repOpt = repertoireRepository.findById(repertoireId);
        Optional<Song> songOpt = songRepository.findById(songId);
        if (repOpt.isEmpty() || songOpt.isEmpty()) return null;

        Repertoire repertoire = repOpt.get();
        Song song = songOpt.get();

        if (song.getRepertoire() != null && !song.getRepertoire().getId().equals(repertoire.getId())) {
            throw new IllegalStateException("Song already belongs to another repertoire");
        }

        if (!repertoire.getSongs().contains(song)) {
            // uses helper from Repertoire(model).
            repertoire.addSong(song);
            repertoireRepository.save(repertoire);
        }
        return song;
    }

    // Remove a Song from a Repertoire using entity helpers.
    // Returns true if removed; false if not found or not associated.
    @Transactional
    public boolean removeSongFromRepertoire(Long repertoireId, Long songId) {
        Optional<Repertoire> repOpt = repertoireRepository.findById(repertoireId);
        Optional<Song> songOpt = songRepository.findById(songId);
        if (repOpt.isEmpty() || songOpt.isEmpty()) return false;

        Repertoire repertoire = repOpt.get();
        Song song = songOpt.get();

        if (song.getRepertoire() == null || !repertoire.getId().equals(song.getRepertoire().getId())) {
            return false;
        }

        // uses helper from Repertoire(model).
        repertoire.removeSong(song);
        repertoireRepository.save(repertoire);
        return true;
    }
}