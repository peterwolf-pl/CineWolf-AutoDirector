package pl.peterwolf.cinewolf.montage.plan;

public record MontagePlanStatistics(
        int plannedShotCount,
        int enabledShotCount,
        int coveredEventCount,
        int distinctShotTypeCount,
        int distinctTargetCount,
        double plannedOutputDurationSeconds,
        double shotDiversityScore
) {
    public MontagePlanStatistics {
        plannedShotCount = Math.max(0, plannedShotCount);
        enabledShotCount = Math.max(0, enabledShotCount);
        coveredEventCount = Math.max(0, coveredEventCount);
        distinctShotTypeCount = Math.max(0, distinctShotTypeCount);
        distinctTargetCount = Math.max(0, distinctTargetCount);
        plannedOutputDurationSeconds = Double.isFinite(plannedOutputDurationSeconds)
                ? Math.max(0.0, plannedOutputDurationSeconds) : 0.0;
        shotDiversityScore = Double.isFinite(shotDiversityScore)
                ? Math.max(0.0, Math.min(1.0, shotDiversityScore)) : 0.0;
    }
}
