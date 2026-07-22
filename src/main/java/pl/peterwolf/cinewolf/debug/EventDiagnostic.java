package pl.peterwolf.cinewolf.debug;

import pl.peterwolf.cinewolf.montage.event.ReplayEventType;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record EventDiagnostic(
        UUID eventId,
        ReplayEventType type,
        double confidence,
        double finalScore,
        String strength,
        List<String> scoringReasons,
        List<FalsePositiveHint> falsePositiveHints
) {
    public EventDiagnostic {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        confidence = Double.isFinite(confidence) ? Math.max(0.0, Math.min(1.0, confidence)) : 0.0;
        finalScore = Double.isFinite(finalScore) ? finalScore : 0.0;
        strength = Objects.requireNonNullElse(strength, strengthFor(confidence));
        scoringReasons = List.copyOf(Objects.requireNonNullElse(scoringReasons, List.of()));
        falsePositiveHints = List.copyOf(Objects.requireNonNullElse(falsePositiveHints, List.of()));
    }

    public static String strengthFor(double confidence) {
        if (confidence >= 0.75) return "STRONG";
        if (confidence >= 0.45) return "PROBABLE";
        return "WEAK";
    }
}
