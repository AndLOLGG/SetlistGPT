package dk.ek.setlistgpt.profile;

public record SessionProfileDto(Long id, String name, ProfileType type) {
    public static SessionProfileDto from(Profile p) {
        return new SessionProfileDto(p.getId(), p.getName(), p.getType());
    }
}
