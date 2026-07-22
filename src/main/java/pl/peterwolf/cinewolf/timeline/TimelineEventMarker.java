package pl.peterwolf.cinewolf.timeline;

import pl.peterwolf.cinewolf.montage.event.ReplayEventType;

import java.util.Objects;
import java.util.UUID;

public record TimelineEventMarker(
        UUID eventId,
        ReplayEventType type,
        long startReplayTime,
        long peakReplayTime,
        long endReplayTime,
        double confidence,
        String strength,
        double finalScore,
        String label
) {
    public TimelineEventMarker {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        strength = Objects.requireNonNullElse(strength, "PROBABLE");
        label = Objects.requireNonNullElse(label, type.name());
    }
}
