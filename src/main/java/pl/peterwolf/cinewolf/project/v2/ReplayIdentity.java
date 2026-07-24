package pl.peterwolf.cinewolf.project.v2;

import java.time.Instant;
import java.util.Objects;

public record ReplayIdentity(
        String stableId,
        String displayName,
        long replayDuration,
        Instant replayCreatedAt,
        String metadataFingerprint,
        String fileFingerprint
) {
    public ReplayIdentity {
        stableId = Objects.requireNonNullElse(stableId, "unknown");
        displayName = Objects.requireNonNullElse(displayName, stableId);
        replayDuration = Math.max(0L, replayDuration);
        replayCreatedAt = replayCreatedAt == null ? Instant.EPOCH : replayCreatedAt;
        metadataFingerprint = Objects.requireNonNullElse(metadataFingerprint, "");
        fileFingerprint = Objects.requireNonNullElse(fileFingerprint, "");
    }
}
