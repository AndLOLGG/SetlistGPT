package dk.ek.setlistgpt.profile;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.servlet.http.HttpServletRequest;

public enum ProfileType {
    ADMIN(3),
    MUSICIAN(2),
    GUEST(1);

    private final int level;
    ProfileType(int level) { this.level = level; }

    // Returns true if access should be denied (lower level than required)
    public boolean verifyAccessLevel(HttpServletRequest request) {
        if (request == null) return true;
        var session = request.getSession(false);
        if (session == null) return true;
        Object obj = session.getAttribute("profile");
        if (!(obj instanceof Profile)) return true;
        Profile p = (Profile) obj;
        if (p.getType() == null) return true;
        return p.getType().level < this.level;
    }

    @JsonCreator
    public static ProfileType fromJson(String value) {
        if (value == null) return null;
        String norm = value.trim().replaceAll("[\\s\\-]+", "_").toUpperCase();
        for (ProfileType t : values()) {
            if (t.name().equals(norm)) return t;
        }
        throw new IllegalArgumentException("Unknown ProfileType: " + value);
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}
