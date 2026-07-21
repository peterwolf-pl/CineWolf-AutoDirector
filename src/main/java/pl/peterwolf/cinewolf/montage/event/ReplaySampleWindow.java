package pl.peterwolf.cinewolf.montage.event;

import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.montage.analysis.MovementMetrics;
import pl.peterwolf.cinewolf.montage.analysis.ReplayEntitySnapshot;
import pl.peterwolf.cinewolf.montage.analysis.ReplaySample;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record ReplaySampleWindow(
        List<ReplaySample> samples,
        Map<TargetReference, List<MovementMetrics>> movementMetrics,
    Set<TargetReference> targetFilter
) {
    public ReplaySampleWindow {
        samples = Objects.requireNonNullElse(samples, List.<ReplaySample>of()).stream()
                .sorted(Comparator.comparingLong(ReplaySample::replayTime)).toList();
        LinkedHashMap<TargetReference, List<MovementMetrics>> copied = new LinkedHashMap<>();
        if (movementMetrics != null) movementMetrics.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(target -> target.uuid().toString())))
                .forEach(entry -> copied.put(entry.getKey(), entry.getValue().stream()
                        .sorted(Comparator.comparingLong(MovementMetrics::replayTime)).toList()));
        movementMetrics = Collections.unmodifiableMap(copied);
        targetFilter = Set.copyOf(Objects.requireNonNullElse(targetFilter, Set.of()));
    }

    public boolean includes(TargetReference target) {
        return targetFilter.isEmpty() || targetFilter.contains(target);
    }

    public List<MovementMetrics> metricsFor(TargetReference target) {
        return movementMetrics.getOrDefault(target, List.of());
    }

    public Optional<ReplayEntitySnapshot> snapshotAt(TargetReference target, long replayTime) {
        return samples.stream().filter(sample -> sample.replayTime() == replayTime)
                .map(sample -> sample.entities().get(target)).filter(Objects::nonNull).findFirst();
    }
}
