package pl.peterwolf.cinewolf.montage.analysis;

public record AnalysisLimits(int maximumTrackedEntities, int maximumSamples, int maximumEvents) {
    public AnalysisLimits {
        maximumTrackedEntities = Math.max(1, Math.min(512, maximumTrackedEntities));
        maximumSamples = Math.max(2, Math.min(200_000, maximumSamples));
        maximumEvents = Math.max(1, Math.min(50_000, maximumEvents));
    }

    public static AnalysisLimits defaults() {
        return new AnalysisLimits(64, 20_000, 5_000);
    }
}
