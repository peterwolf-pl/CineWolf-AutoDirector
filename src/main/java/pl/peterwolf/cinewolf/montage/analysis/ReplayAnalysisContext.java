package pl.peterwolf.cinewolf.montage.analysis;

import pl.peterwolf.cinewolf.montage.event.DetectorThresholds;
import pl.peterwolf.cinewolf.montage.event.EventScoringProfile;
import pl.peterwolf.cinewolf.montage.event.ReplayEventType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ReplayAnalysisContext(
        List<ReplaySample> samples,
        Map<ReplayEventType, Double> presetEventWeights,
        DetectorThresholds detectorThresholds,
        EventScoringProfile scoringProfile,
        AnalysisLimits limits
) {
    public ReplayAnalysisContext {
        samples = List.copyOf(Objects.requireNonNullElse(samples, List.of()));
        EnumMap<ReplayEventType, Double> weights = new EnumMap<>(ReplayEventType.class);
        if (presetEventWeights != null) {
            presetEventWeights.forEach((type, value) -> {
                if (type != null && value != null && Double.isFinite(value) && value >= 0.0) {
                    weights.put(type, value);
                }
            });
        }
        presetEventWeights = Collections.unmodifiableMap(weights);
        detectorThresholds = Objects.requireNonNullElseGet(detectorThresholds, DetectorThresholds::defaults);
        scoringProfile = Objects.requireNonNullElseGet(scoringProfile, EventScoringProfile::defaults);
        limits = Objects.requireNonNullElseGet(limits, AnalysisLimits::defaults);
    }

    public static ReplayAnalysisContext defaults(List<ReplaySample> samples) {
        return new ReplayAnalysisContext(samples, Map.of(), DetectorThresholds.defaults(),
                EventScoringProfile.defaults(), AnalysisLimits.defaults());
    }
}
