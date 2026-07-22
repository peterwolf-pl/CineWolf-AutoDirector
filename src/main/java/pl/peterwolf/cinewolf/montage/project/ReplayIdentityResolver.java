package pl.peterwolf.cinewolf.montage.project;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Deterministic local replay identity used for project binding without external services. */
public final class ReplayIdentityResolver {
    public UUID resolve(String replayPathOrName, long sourceStart, long sourceEnd) {
        String key = Objects.requireNonNullElse(replayPathOrName, "unknown")
                + "|" + sourceStart + "|" + sourceEnd;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    public UUID resolve(UUID provided, String replayPathOrName, long sourceStart, long sourceEnd) {
        return provided != null ? provided : resolve(replayPathOrName, sourceStart, sourceEnd);
    }
}
