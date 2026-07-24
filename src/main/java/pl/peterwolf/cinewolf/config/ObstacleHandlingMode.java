package pl.peterwolf.cinewolf.config;

/**
 * How CineWolf deals with geometry between the camera and the subject.
 * <ul>
 *   <li>{@link #AVOID} – move the camera path around solid blocks (existing collision pass)</li>
 *   <li>{@link #CLIP} – keep the camera path; hide occluding blocks/entities while replaying</li>
 *   <li>{@link #NONE} – neither path adjustment nor visual clipping</li>
 * </ul>
 */
public enum ObstacleHandlingMode {
    AVOID,
    CLIP,
    NONE;

    public boolean adjustsCameraPath() {
        return this == AVOID;
    }

    public boolean clipsOccluders() {
        return this == CLIP;
    }

    public static ObstacleHandlingMode fromLegacy(boolean collisionAvoidance) {
        return collisionAvoidance ? AVOID : NONE;
    }

    public static ObstacleHandlingMode parse(String raw, ObstacleHandlingMode fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return ObstacleHandlingMode.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
