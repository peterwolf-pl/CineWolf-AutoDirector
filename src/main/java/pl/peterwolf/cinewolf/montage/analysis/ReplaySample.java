package pl.peterwolf.cinewolf.montage.analysis;

import pl.peterwolf.cinewolf.model.TargetReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ReplaySample(
        long replayTime,
        Map<TargetReference, ReplayEntitySnapshot> entities,
        List<ReplayMarkerSnapshot> markers,
        List<ObservedReplayAction> actions
) {
    public ReplaySample {
        if (replayTime < 0) throw new IllegalArgumentException("Sample replay time cannot be negative");
        Map<TargetReference, ReplayEntitySnapshot> source = entities == null ? Map.of() : entities;
        List<Map.Entry<TargetReference, ReplayEntitySnapshot>> entries = new ArrayList<>(source.entrySet());
        entries.sort(Map.Entry.comparingByKey(Comparator.comparing(value -> value.uuid().toString())));
        LinkedHashMap<TargetReference, ReplayEntitySnapshot> ordered = new LinkedHashMap<>();
        for (Map.Entry<TargetReference, ReplayEntitySnapshot> entry : entries) {
            if (!entry.getKey().equals(entry.getValue().target())) {
                throw new IllegalArgumentException("Entity snapshot key and target must match");
            }
            ordered.put(entry.getKey(), entry.getValue());
        }
        entities = Collections.unmodifiableMap(ordered);
        markers = List.copyOf(Objects.requireNonNullElse(markers, List.of()));
        actions = List.copyOf(Objects.requireNonNullElse(actions, List.of()));
    }

    public static ReplaySample empty(long replayTime) {
        return new ReplaySample(replayTime, Map.of(), List.of(), List.of());
    }
}
