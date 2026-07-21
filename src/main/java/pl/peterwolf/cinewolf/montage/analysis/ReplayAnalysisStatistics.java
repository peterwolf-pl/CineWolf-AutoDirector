package pl.peterwolf.cinewolf.montage.analysis;

import pl.peterwolf.cinewolf.montage.event.ReplayEventType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public record ReplayAnalysisStatistics(
        long analyzedDurationTicks,
        int inputSampleCount,
        int coarseSampleCount,
        int detailedSampleCount,
        int analyzedSampleCount,
        int entityCount,
        int detectedEventCount,
        int mergedEventCount,
        int sceneCount,
        Map<ReplayEventType, Integer> eventCounts
) {
    public ReplayAnalysisStatistics {
        analyzedDurationTicks = Math.max(0L, analyzedDurationTicks);
        inputSampleCount = Math.max(0, inputSampleCount);
        coarseSampleCount = Math.max(0, coarseSampleCount);
        detailedSampleCount = Math.max(0, detailedSampleCount);
        analyzedSampleCount = Math.max(0, analyzedSampleCount);
        entityCount = Math.max(0, entityCount);
        detectedEventCount = Math.max(0, detectedEventCount);
        mergedEventCount = Math.max(0, mergedEventCount);
        sceneCount = Math.max(0, sceneCount);
        EnumMap<ReplayEventType, Integer> counts = new EnumMap<>(ReplayEventType.class);
        if (eventCounts != null) eventCounts.forEach((type, count) -> counts.put(type, Math.max(0, count)));
        eventCounts = Collections.unmodifiableMap(counts);
    }
}
