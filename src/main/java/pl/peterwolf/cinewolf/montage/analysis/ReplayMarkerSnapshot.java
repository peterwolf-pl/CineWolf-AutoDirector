package pl.peterwolf.cinewolf.montage.analysis;

import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ReplayMarkerSnapshot(
        UUID markerId,
        long replayTime,
        String label,
        Optional<Vec3d> location
) {
    public ReplayMarkerSnapshot {
        Objects.requireNonNull(markerId, "markerId");
        if (replayTime < 0) throw new IllegalArgumentException("Marker replay time cannot be negative");
        label = Objects.requireNonNullElse(label, "");
        location = Objects.requireNonNullElse(location, Optional.empty());
    }
}
