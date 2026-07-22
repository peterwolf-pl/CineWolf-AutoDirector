package pl.peterwolf.cinewolf.debug;

import java.util.Objects;

public record FalsePositiveHint(
        String code,
        String severity,
        String message,
        double confidence,
        boolean probable
) {
    public FalsePositiveHint {
        code = Objects.requireNonNullElse(code, "unknown");
        severity = Objects.requireNonNullElse(severity, "INFO");
        message = Objects.requireNonNullElse(message, "");
        confidence = Double.isFinite(confidence) ? Math.max(0.0, Math.min(1.0, confidence)) : 0.0;
    }

    public static FalsePositiveHint weak(String code, String message, double confidence) {
        return new FalsePositiveHint(code, "WARNING", message, confidence, false);
    }

    public static FalsePositiveHint probable(String code, String message, double confidence) {
        return new FalsePositiveHint(code, "INFO", message, confidence, true);
    }
}
