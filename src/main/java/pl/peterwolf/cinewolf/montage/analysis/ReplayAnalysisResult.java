package pl.peterwolf.cinewolf.montage.analysis;

import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.montage.event.ReplayEvent;
import pl.peterwolf.cinewolf.montage.event.ScoredReplayEvent;
import pl.peterwolf.cinewolf.montage.scene.ReplayScene;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record ReplayAnalysisResult(
        ReplayAnalysisRequest request,
        SampleSelection sampleSelection,
        List<ReplaySample> samples,
        Map<TargetReference, List<MovementMetrics>> movementMetrics,
        List<ReplayEvent> detectedEvents,
        List<ReplayEvent> mergedEvents,
        List<ScoredReplayEvent> rankedEvents,
        List<ReplayScene> scenes,
        List<RankedReplayTarget> rankedTargets,
        ReplayAnalysisStatistics statistics,
        List<AnalysisWarning> warnings
) {
    public ReplayAnalysisResult {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(sampleSelection, "sampleSelection");
        samples = List.copyOf(Objects.requireNonNullElse(samples, List.of()));
        LinkedHashMap<TargetReference, List<MovementMetrics>> copiedMetrics = new LinkedHashMap<>();
        if (movementMetrics != null) movementMetrics.forEach((target, values) -> copiedMetrics.put(target, List.copyOf(values)));
        movementMetrics = Collections.unmodifiableMap(copiedMetrics);
        detectedEvents = List.copyOf(Objects.requireNonNullElse(detectedEvents, List.of()));
        mergedEvents = List.copyOf(Objects.requireNonNullElse(mergedEvents, List.of()));
        rankedEvents = List.copyOf(Objects.requireNonNullElse(rankedEvents, List.of()));
        scenes = List.copyOf(Objects.requireNonNullElse(scenes, List.of()));
        rankedTargets = List.copyOf(Objects.requireNonNullElse(rankedTargets, List.of()));
        Objects.requireNonNull(statistics, "statistics");
        warnings = List.copyOf(Objects.requireNonNullElse(warnings, List.of()));
    }

    public Optional<RankedReplayTarget> primaryTarget() {
        return rankedTargets.stream().findFirst();
    }
}
