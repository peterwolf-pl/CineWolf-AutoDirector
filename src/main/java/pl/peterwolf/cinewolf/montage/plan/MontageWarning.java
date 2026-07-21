package pl.peterwolf.cinewolf.montage.plan;

import java.util.List;
import java.util.Objects;

public record MontageWarning(String code, Severity severity, List<String> arguments) {
    public MontageWarning {
        code = Objects.requireNonNullElse(code, "unknown");
        severity = Objects.requireNonNullElse(severity, Severity.WARNING);
        arguments = List.copyOf(Objects.requireNonNullElse(arguments, List.of()));
    }

    public static MontageWarning warning(String code, Object... arguments) {
        return new MontageWarning(code, Severity.WARNING,
                java.util.Arrays.stream(arguments).map(String::valueOf).toList());
    }

    public enum Severity { INFO, WARNING, ERROR }
}
