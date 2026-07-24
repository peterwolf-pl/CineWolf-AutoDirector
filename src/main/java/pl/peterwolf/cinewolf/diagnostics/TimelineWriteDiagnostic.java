package pl.peterwolf.cinewolf.diagnostics;

import java.util.List;
import java.util.Objects;

public record TimelineWriteDiagnostic(
        String operationId,
        boolean success,
        String mode,
        int cameraKeyframes,
        int fovKeyframes,
        int replayTimeKeyframes,
        boolean rolledBack,
        boolean usedNativeUndo,
        List<String> conflicts,
        List<String> warnings,
        String message
) {
    public TimelineWriteDiagnostic {
        operationId = Objects.requireNonNullElse(operationId, "unknown");
        mode = Objects.requireNonNullElse(mode, "unknown");
        conflicts = List.copyOf(conflicts == null ? List.of() : conflicts);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        message = Objects.requireNonNullElse(message, "");
    }
}
