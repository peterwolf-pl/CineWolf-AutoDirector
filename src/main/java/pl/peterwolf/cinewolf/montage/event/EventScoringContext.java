package pl.peterwolf.cinewolf.montage.event;

import pl.peterwolf.cinewolf.model.TargetReference;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record EventScoringContext(
        EventScoringProfile profile,
        Map<ReplayEventType, Double> presetWeights,
        Set<TargetReference> selectedTargets,
        Map<ReplayEventType, Integer> occurrenceCounts,
        Set<Long> replayMarkerTimes
) {
    public EventScoringContext {
        profile = Objects.requireNonNullElseGet(profile, EventScoringProfile::defaults);
        EnumMap<ReplayEventType, Double> weights = new EnumMap<>(ReplayEventType.class);
        if (presetWeights != null) weights.putAll(presetWeights);
        presetWeights = Collections.unmodifiableMap(weights);
        selectedTargets = Set.copyOf(Objects.requireNonNullElse(selectedTargets, Set.of()));
        EnumMap<ReplayEventType, Integer> counts = new EnumMap<>(ReplayEventType.class);
        if (occurrenceCounts != null) counts.putAll(occurrenceCounts);
        occurrenceCounts = Collections.unmodifiableMap(counts);
        replayMarkerTimes = Set.copyOf(Objects.requireNonNullElse(replayMarkerTimes, Set.of()));
    }
}
