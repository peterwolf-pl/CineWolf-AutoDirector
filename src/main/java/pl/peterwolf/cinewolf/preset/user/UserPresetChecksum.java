package pl.peterwolf.cinewolf.preset.user;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

public final class UserPresetChecksum {
    private UserPresetChecksum() {
    }

    public static String compute(UserMontagePresetFile fileWithoutChecksum) {
        Objects.requireNonNull(fileWithoutChecksum, "file");
        String payload = fileWithoutChecksum.schemaVersion() + "|"
                + fileWithoutChecksum.id() + "|"
                + fileWithoutChecksum.displayName() + "|"
                + fileWithoutChecksum.targetDurationSeconds() + "|"
                + fileWithoutChecksum.aspectRatio() + "|"
                + fileWithoutChecksum.pacing() + "|"
                + fileWithoutChecksum.minimumShotDuration() + "|"
                + fileWithoutChecksum.maximumShotDuration() + "|"
                + fileWithoutChecksum.minimumShotCount() + "|"
                + fileWithoutChecksum.maximumShotCount() + "|"
                + fileWithoutChecksum.preferredShotTypes() + "|"
                + fileWithoutChecksum.eventWeights() + "|"
                + fileWithoutChecksum.introTemplate() + "|"
                + fileWithoutChecksum.outroTemplate() + "|"
                + fileWithoutChecksum.style();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).toLowerCase(Locale.ROOT);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to compute preset checksum", exception);
        }
    }

    public static boolean matches(UserMontagePresetFile file) {
        if (file.checksum() == null || file.checksum().isBlank()) return true;
        UserMontagePresetFile without = new UserMontagePresetFile(
                file.schemaVersion(), file.id(), file.displayName(), file.description(),
                file.targetDurationSeconds(), file.aspectRatio(), file.pacing(),
                file.minimumShotDuration(), file.maximumShotDuration(), file.minimumShotCount(),
                file.maximumShotCount(), file.preferredShotTypes(), file.eventWeights(),
                file.introTemplate(), file.outroTemplate(), file.style(), "");
        return compute(without).equalsIgnoreCase(file.checksum().trim());
    }
}
