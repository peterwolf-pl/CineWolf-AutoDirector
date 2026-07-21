package pl.peterwolf.cinewolf.montage.plan;

import pl.peterwolf.cinewolf.config.ShotDiversityConfig;

/** Immutable snapshot of user-configurable diversity rewards and penalties. */
public record ShotDiversityProfile(
        double baseScore,
        double shotTypeCoverageWeight,
        double framingCoverageWeight,
        double alternateShotReward,
        double alternateFramingReward,
        double repeatedShotPenalty,
        double repeatedFramingPenalty,
        double repeatedEventPenalty,
        double repeatedTargetAndShotPenalty,
        double similarAzimuthPenalty,
        double similarDistancePenalty,
        double similarHeightPenalty,
        double repeatedMovementDirectionPenalty,
        double alternateMovementDirectionReward,
        double differentHeightReward
) {
    public ShotDiversityProfile {
        baseScore = nonNegative(baseScore, 0.45);
        shotTypeCoverageWeight = nonNegative(shotTypeCoverageWeight, 0.45);
        framingCoverageWeight = nonNegative(framingCoverageWeight, 0.25);
        alternateShotReward = nonNegative(alternateShotReward, 0.12);
        alternateFramingReward = nonNegative(alternateFramingReward, 0.08);
        repeatedShotPenalty = nonNegative(repeatedShotPenalty, 0.22);
        repeatedFramingPenalty = nonNegative(repeatedFramingPenalty, 0.09);
        repeatedEventPenalty = nonNegative(repeatedEventPenalty, 0.08);
        repeatedTargetAndShotPenalty = nonNegative(repeatedTargetAndShotPenalty, 0.08);
        similarAzimuthPenalty = nonNegative(similarAzimuthPenalty, 0.08);
        similarDistancePenalty = nonNegative(similarDistancePenalty, 0.08);
        similarHeightPenalty = nonNegative(similarHeightPenalty, 0.06);
        repeatedMovementDirectionPenalty = nonNegative(repeatedMovementDirectionPenalty, 0.06);
        alternateMovementDirectionReward = nonNegative(alternateMovementDirectionReward, 0.05);
        differentHeightReward = nonNegative(differentHeightReward, 0.04);
    }

    public static ShotDiversityProfile defaults() {
        return new ShotDiversityProfile(0.45, 0.45, 0.25, 0.12, 0.08,
                0.22, 0.09, 0.08, 0.08, 0.08, 0.08, 0.06, 0.06, 0.05, 0.04);
    }

    public static ShotDiversityProfile from(ShotDiversityConfig config) {
        if (config == null) return defaults();
        return new ShotDiversityProfile(config.baseScore, config.shotTypeCoverageWeight,
                config.framingCoverageWeight, config.alternateShotReward, config.alternateFramingReward,
                config.repeatedShotPenalty, config.repeatedFramingPenalty, config.repeatedEventPenalty,
                config.repeatedTargetAndShotPenalty, config.similarAzimuthPenalty,
                config.similarDistancePenalty, config.similarHeightPenalty,
                config.repeatedMovementDirectionPenalty, config.alternateMovementDirectionReward,
                config.differentHeightReward);
    }

    private static double nonNegative(double value, double fallback) {
        return Double.isFinite(value) && value >= 0.0 ? Math.min(10.0, value) : fallback;
    }
}
