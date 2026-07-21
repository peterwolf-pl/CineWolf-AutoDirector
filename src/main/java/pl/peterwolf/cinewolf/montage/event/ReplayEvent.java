package pl.peterwolf.cinewolf.montage.event;

import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ReplayEvent(
        UUID eventId,
        ReplayEventType type,
        long startReplayTime,
        long peakReplayTime,
        long endReplayTime,
        Set<TargetReference> targets,
        Vec3d location,
        double magnitude,
        double confidence,
        EventEvidence evidence
) {
    public ReplayEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        if (startReplayTime < 0 || peakReplayTime < startReplayTime || endReplayTime < peakReplayTime) {
            throw new IllegalArgumentException("Invalid replay event interval");
        }
        targets = Set.copyOf(Objects.requireNonNullElse(targets, Set.of()));
        location = Objects.requireNonNullElse(location, Vec3d.ZERO);
        magnitude = clamp01(magnitude);
        confidence = clamp01(confidence);
        evidence = Objects.requireNonNull(evidence, "evidence");
    }

    public static ReplayEvent create(ReplayEventType type, long start, long peak, long end,
                                     Set<TargetReference> targets, Vec3d location,
                                     double magnitude, double confidence, EventEvidence evidence) {
        return new ReplayEvent(stableId(type, start, peak, end, targets), type, start, peak, end,
                targets, location, magnitude, confidence, evidence);
    }

    public ReplayEvent withEvidence(EventEvidence replacement) {
        return new ReplayEvent(eventId, type, startReplayTime, peakReplayTime, endReplayTime,
                targets, location, magnitude, confidence, replacement);
    }

    public long durationTicks() {
        return endReplayTime - startReplayTime;
    }

    private static UUID stableId(ReplayEventType type, long start, long peak, long end,
                                 Set<TargetReference> targets) {
        List<TargetReference> ordered = new ArrayList<>(targets);
        ordered.sort(Comparator.comparing(target -> target.uuid().toString()));
        StringBuilder key = new StringBuilder(type.name()).append(':').append(start).append(':')
                .append(peak).append(':').append(end);
        for (TargetReference target : ordered) key.append(':').append(target.uuid());
        return UUID.nameUUIDFromBytes(key.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static double clamp01(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
    }
}
