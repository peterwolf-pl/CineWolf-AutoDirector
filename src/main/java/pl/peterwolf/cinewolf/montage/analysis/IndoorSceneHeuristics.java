package pl.peterwolf.cinewolf.montage.analysis;

import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;

/**
 * Lightweight indoor detection from sampled subject motion (no world access).
 * Used to shrink framing, prefer room-corner/static shots, and relax AVOID indoors.
 */
public final class IndoorSceneHeuristics {
    /** Max vertical travel (blocks) still considered "one floor / room height". */
    public static final double MAX_INDOOR_HEIGHT_SPAN = 3.25;
    /** Max horizontal span of the subject path still treated as a single room. */
    public static final double MAX_INDOOR_HORIZONTAL_SPAN = 28.0;
    public static final double MIN_INDOOR_HORIZONTAL_SPAN = 0.75;

    private IndoorSceneHeuristics() {
    }

    public static boolean isLikelyIndoor(ReplayAnalysisResult analysis, TargetReference target,
                                        long startTick, long endTick) {
        if (analysis == null || target == null || endTick <= startTick) return false;
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        int count = 0;
        for (ReplaySample sample : analysis.samples()) {
            if (sample.replayTime() < startTick || sample.replayTime() > endTick) continue;
            ReplayEntitySnapshot entity = sample.entities().get(target);
            if (entity == null || entity.pose() == null || !entity.pose().isFinite()) continue;
            Vec3d p = entity.pose().position();
            minX = Math.min(minX, p.x());
            maxX = Math.max(maxX, p.x());
            minY = Math.min(minY, p.y());
            maxY = Math.max(maxY, p.y());
            minZ = Math.min(minZ, p.z());
            maxZ = Math.max(maxZ, p.z());
            count++;
        }
        if (count < 2) return false;
        double heightSpan = maxY - minY;
        double horizSpan = Math.max(maxX - minX, maxZ - minZ);
        // Indoor rooms: little vertical travel, path fits in a modest horizontal box (not open field).
        return heightSpan <= MAX_INDOOR_HEIGHT_SPAN
                && horizSpan >= MIN_INDOOR_HORIZONTAL_SPAN
                && horizSpan <= MAX_INDOOR_HORIZONTAL_SPAN;
    }
}
