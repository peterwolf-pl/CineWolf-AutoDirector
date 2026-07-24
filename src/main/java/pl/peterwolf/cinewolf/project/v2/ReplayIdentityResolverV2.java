package pl.peterwolf.cinewolf.project.v2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Stable replay identity without storing private absolute paths by default. */
public final class ReplayIdentityResolverV2 {
    public ReplayIdentity resolve(
            String displayName,
            String metadataId,
            long totalTicks,
            Instant createdAt,
            String fileNameOnly
    ) {
        String name = Objects.requireNonNullElse(displayName, "replay");
        String meta = Objects.requireNonNullElse(metadataId, "");
        String file = Objects.requireNonNullElse(fileNameOnly, "");
        // Never hash absolute private paths; only file name + metadata id + duration.
        String payload = meta + "|" + file + "|" + totalTicks;
        String stable = uuidFrom(payload).toString();
        return new ReplayIdentity(
                stable,
                name,
                Math.max(0L, totalTicks),
                createdAt == null ? Instant.EPOCH : createdAt,
                sha(meta + "|" + totalTicks),
                sha(file + "|" + totalTicks)
        );
    }

    public boolean matches(ReplayIdentity expected, ReplayIdentity actual) {
        if (expected == null || actual == null) return false;
        if (expected.stableId().equals(actual.stableId())) return true;
        if (!expected.metadataFingerprint().isBlank()
                && expected.metadataFingerprint().equals(actual.metadataFingerprint())) {
            return true;
        }
        return !expected.fileFingerprint().isBlank()
                && expected.fileFingerprint().equals(actual.fileFingerprint());
    }

    private static UUID uuidFrom(String payload) {
        return UUID.nameUUIDFromBytes(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)))
                    .toLowerCase(Locale.ROOT);
        } catch (Exception exception) {
            return Integer.toHexString(payload.hashCode());
        }
    }
}
