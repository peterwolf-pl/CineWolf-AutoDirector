package pl.peterwolf.cinewolf.diagnostics;

import java.util.List;
import java.util.Objects;

public record EventDiagnostic(
        String eventId,
        String detectorId,
        String eventType,
        String source,
        double confidence,
        double finalScore,
        String strength,
        List<String> measurements,
        List<String> thresholds,
        List<String> mergeHistory,
        List<String> rejectionReasons,
        List<String> relatedEvents,
        List<String> falsePositiveHints
) {
    public EventDiagnostic {
        eventId = Objects.requireNonNullElse(eventId, "unknown");
        detectorId = Objects.requireNonNullElse(detectorId, "unknown");
        eventType = Objects.requireNonNullElse(eventType, "unknown");
        source = Objects.requireNonNullElse(source, "unknown");
        measurements = List.copyOf(measurements == null ? List.of() : measurements);
        thresholds = List.copyOf(thresholds == null ? List.of() : thresholds);
        mergeHistory = List.copyOf(mergeHistory == null ? List.of() : mergeHistory);
        rejectionReasons = List.copyOf(rejectionReasons == null ? List.of() : rejectionReasons);
        relatedEvents = List.copyOf(relatedEvents == null ? List.of() : relatedEvents);
        falsePositiveHints = List.copyOf(falsePositiveHints == null ? List.of() : falsePositiveHints);
        strength = Objects.requireNonNullElse(strength, "unknown");
    }
}
