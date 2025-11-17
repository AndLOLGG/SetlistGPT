package dk.ek.setlistgpt.repertoire;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum RepertoireVisibility {
    PUBLIC,
    AUTHENTICATED, // visible to authenticated profiles (logged‑in users)
    PRIVATE;

    @JsonCreator
    public static RepertoireVisibility fromJson(String value) {
        if (value == null) return null;
        String norm = value.trim().replaceAll("[\\s\\-]+", "_").toUpperCase();
        for (RepertoireVisibility v : values()) {
            if (v.name().equals(norm)) return v;
        }
        throw new IllegalArgumentException("Unknown visibility: " + value);
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}