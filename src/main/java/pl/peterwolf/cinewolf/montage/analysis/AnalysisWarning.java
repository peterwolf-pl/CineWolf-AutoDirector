package pl.peterwolf.cinewolf.montage.analysis;

import java.util.List;
import java.util.Objects;

public record AnalysisWarning(String code, Severity severity, List<String> arguments) {
    public AnalysisWarning {
        code = Objects.requireNonNullElse(code, "unknown");
        severity = Objects.requireNonNullElse(severity, Severity.WARNING);
        arguments = List.copyOf(Objects.requireNonNullElse(arguments, List.of()));
    }

    public static AnalysisWarning warning(String code, Object... arguments) {
        return new AnalysisWarning(code, Severity.WARNING,
                java.util.Arrays.stream(arguments).map(String::valueOf).toList());
    }

    public enum Severity { INFO, WARNING, ERROR }
}
