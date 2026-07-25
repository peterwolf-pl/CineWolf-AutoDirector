package pl.peterwolf.cinewolf.camera;

import pl.peterwolf.cinewolf.model.Vec3d;

/**
 * Keeps the camera under a solid ceiling above the subject (or the camera column).
 * When the player is indoors, crane/orbit heights must not pierce the roof.
 */
public final class CeilingClearanceClamp {
    /** Default gap between camera and the underside of the ceiling block. */
    public static final double DEFAULT_CLEARANCE = 0.35;
    /** Ignore "ceilings" farther than this above the subject head (open sky). */
    public static final double DEFAULT_MAX_PROBE = 24.0;

    private CeilingClearanceClamp() {
    }

    /**
     * @param ceilingY world Y of the ceiling underside hit, or empty if open sky
     * @param clearance blocks of air under the ceiling for the camera
     * @return maximum allowed camera Y, or empty when no ceiling constraint applies
     */
    public static java.util.OptionalDouble maxCameraY(java.util.OptionalDouble ceilingY, double clearance) {
        if (ceilingY.isEmpty()) return java.util.OptionalDouble.empty();
        double gap = Double.isFinite(clearance) ? Math.max(0.05, clearance) : DEFAULT_CLEARANCE;
        return java.util.OptionalDouble.of(ceilingY.getAsDouble() - gap);
    }

    /** Pull the camera down so {@code position.y <= maxY}, preserving XZ. */
    public static Vec3d clamp(Vec3d position, double maxY) {
        if (position == null || !position.isFinite() || !Double.isFinite(maxY)) return position;
        if (position.y() <= maxY) return position;
        return new Vec3d(position.x(), maxY, position.z());
    }

    public static Vec3d clamp(Vec3d position, java.util.OptionalDouble maxY) {
        if (maxY.isEmpty()) return position;
        return clamp(position, maxY.getAsDouble());
    }
}
