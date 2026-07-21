package pl.peterwolf.cinewolf.config;

/** User-tunable diversity rewards/penalties consumed by the montage planner. */
public final class ShotDiversityConfig {
    public double baseScore = 0.45;
    public double shotTypeCoverageWeight = 0.45;
    public double framingCoverageWeight = 0.25;
    public double alternateShotReward = 0.12;
    public double alternateFramingReward = 0.08;
    public double repeatedShotPenalty = 0.22;
    public double repeatedFramingPenalty = 0.09;
    public double repeatedEventPenalty = 0.08;
    public double repeatedTargetAndShotPenalty = 0.08;
    public double similarAzimuthPenalty = 0.08;
    public double similarDistancePenalty = 0.08;
    public double similarHeightPenalty = 0.06;
    public double repeatedMovementDirectionPenalty = 0.06;
    public double alternateMovementDirectionReward = 0.05;
    public double differentHeightReward = 0.04;

    public void normalize() {
        baseScore = finiteNonNegative(baseScore, 0.45);
        shotTypeCoverageWeight = finiteNonNegative(shotTypeCoverageWeight, 0.45);
        framingCoverageWeight = finiteNonNegative(framingCoverageWeight, 0.25);
        alternateShotReward = finiteNonNegative(alternateShotReward, 0.12);
        alternateFramingReward = finiteNonNegative(alternateFramingReward, 0.08);
        repeatedShotPenalty = finiteNonNegative(repeatedShotPenalty, 0.22);
        repeatedFramingPenalty = finiteNonNegative(repeatedFramingPenalty, 0.09);
        repeatedEventPenalty = finiteNonNegative(repeatedEventPenalty, 0.08);
        repeatedTargetAndShotPenalty = finiteNonNegative(repeatedTargetAndShotPenalty, 0.08);
        similarAzimuthPenalty = finiteNonNegative(similarAzimuthPenalty, 0.08);
        similarDistancePenalty = finiteNonNegative(similarDistancePenalty, 0.08);
        similarHeightPenalty = finiteNonNegative(similarHeightPenalty, 0.06);
        repeatedMovementDirectionPenalty = finiteNonNegative(repeatedMovementDirectionPenalty, 0.06);
        alternateMovementDirectionReward = finiteNonNegative(alternateMovementDirectionReward, 0.05);
        differentHeightReward = finiteNonNegative(differentHeightReward, 0.04);
    }

    private static double finiteNonNegative(double value, double fallback) {
        return Double.isFinite(value) && value >= 0.0 ? Math.min(10.0, value) : fallback;
    }
}
