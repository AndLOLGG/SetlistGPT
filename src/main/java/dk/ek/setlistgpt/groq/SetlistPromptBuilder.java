package dk.ek.setlistgpt.groq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dk.ek.setlistgpt.song.SongDto;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a single textual \"user prompt\" string for the Groq API.
 * The prompt contains:
 * - short instructions (use only provided repertoire)
 * - a JSON payload with the `SetlistAiPrompt` content
 * - explicit normalization / scoring / selection rules matching MoodCalculator semantics
 *
 * Usage:
 *   var prompt = builder.buildUserPrompt(aiPrompt);
 *   groqClient.generateChatCompletion(prompt);
 */
@Component
public class SetlistPromptBuilder {

    private final ObjectMapper mapper;

    public SetlistPromptBuilder() {
        this.mapper = new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true);
    }

    public String buildUserPrompt(SetlistAiPrompt p) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("repertoire", sanitizeRepertoire(p.getRepertoire()));
            payload.put("desiredMood", p.getDesiredMood());
            payload.put("desiredBpm", p.getDesiredBpm());
            payload.put("genreFilter", p.getGenreFilter());
            payload.put("targetDurationSeconds", p.getTargetDurationSeconds());
            payload.put("allowReuse", p.isAllowReuse());
            payload.put("allowOverflow", p.isAllowOverflow());

            StringBuilder sb = new StringBuilder();
            sb.append("You are an assistant that builds a setlist from a provided repertoire. ");
            sb.append("Use only the songs in the input repertoire (do not invent or fetch songs). Follow these rules exactly.\n\n");
            sb.append("Input (JSON):\n");
            sb.append(mapper.writeValueAsString(payload));
            sb.append("\n\n");
            sb.append("Normalization rules:\n");
            sb.append("- Normalize moods/genres by trimming, replacing spaces/hyphens with underscores, and uppercasing (e.g. \"easy going\" -> \"EASY_GOING\").\n");
            sb.append("- If genreFilter is a group name, resolve it into member genres before filtering.\n\n");
            sb.append("Scoring (use project MoodCalculator semantics):\n");
            sb.append("- BPM score: if desiredBpm is null -> 0.5; if song.bpm null -> 0.2; else raw = 1 - (abs(song.bpm - desiredBpm) / 60.0); bpmScore = max(0.1, raw).\n");
            sb.append("- Mood score: if desiredMood null -> 0.5; if song.mood null -> 0.2; exact match -> 1.0; related -> 0.8; otherwise 0.1. Use the project's mood related sets.\n");
            sb.append("- Combine: if both desiredMood and desiredBpm provided: score = 0.7*moodScore + 0.3*bpmScore; if only mood -> moodScore; if only bpm -> bpmScore; if neither -> 0.5.\n\n");
            sb.append("Selection rules:\n");
            sb.append("- Compute score for each candidate and sort by score desc. Break ties by shorter duration, then title (case-insensitive).\n");
            sb.append("- Build setlist by iterating sorted songs and adding if total + song.duration <= targetDurationSeconds.\n");
            sb.append("- If allowReuse true you may add songs multiple times following the ranked order. If false each song at most once.\n");
            sb.append("- If no song fits because every song duration > target, pick the single shortest valid-duration song.\n");
            sb.append("- If allowOverflow true, allow the last added song to exceed target; otherwise do not add a song that causes overflow.\n");
            sb.append("- Stop when total >= targetDurationSeconds or no further additions possible.\n");
            sb.append("- Do not change song durations; compute durationInSeconds = durationMinutes*60 + durationSeconds.\n\n");
            sb.append("Output (JSON): Return a single valid JSON object with these fields:\n");
            sb.append("{\n");
            sb.append("  \"requested\": { \"desiredMood\", \"desiredBpm\", \"genreFilter\", \"targetDurationSeconds\", \"allowReuse\", \"allowOverflow\" },\n");
            sb.append("  \"setlist\": [ { \"id\",\"title\",\"artist\",\"genre\",\"bpm\",\"mood\",\"durationMinutes\",\"durationSeconds\",\"durationInSeconds\",\"score\" }, ... ],\n");
            sb.append("  \"totalDurationSeconds\": integer,\n");
            sb.append("  \"summary\": { \"songCount\": integer, \"avgScore\": number },\n");
            sb.append("  \"notes\": string\n");
            sb.append("}\n\n");
            sb.append("Constraints: Keep output strictly valid JSON and include only a single-line explanatory note in `notes` if needed.\n");

            return sb.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to build user prompt", ex);
        }
    }

    private List<Map<String, Object>> sanitizeRepertoire(List<SongDto> rep) {
        return rep == null ? List.of() : rep.stream().map(s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getId());
            m.put("title", s.getTitle());
            m.put("artist", s.getArtist());
            m.put("genre", s.getGenre() != null ? s.getGenre().name() : null);
            m.put("bpm", s.getBpm());
            m.put("mood", s.getMood() != null ? s.getMood().name() : null);
            m.put("durationMinutes", s.getDurationMinutes());
            m.put("durationSeconds", s.getDurationSeconds());
            m.put("durationInSeconds", s.getDurationInSeconds());
            return m;
        }).toList();
    }
}