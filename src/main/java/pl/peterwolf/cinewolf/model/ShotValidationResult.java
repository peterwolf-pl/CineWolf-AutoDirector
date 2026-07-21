package pl.peterwolf.cinewolf.model;

import java.util.ArrayList;
import java.util.List;

public record ShotValidationResult(List<PathWarning> messages) {
    public ShotValidationResult {
        messages = List.copyOf(messages);
    }

    public static ShotValidationResult valid() {
        return new ShotValidationResult(List.of());
    }

    public boolean isValid() {
        return messages.stream().noneMatch(message -> message.severity() == PathWarning.Severity.ERROR);
    }

    public ShotValidationResult merge(ShotValidationResult other) {
        List<PathWarning> merged = new ArrayList<>(messages);
        merged.addAll(other.messages);
        return new ShotValidationResult(merged);
    }
}
