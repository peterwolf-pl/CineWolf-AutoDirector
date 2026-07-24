package pl.peterwolf.cinewolf.preset.user;

import pl.peterwolf.cinewolf.model.ShotType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class UserPresetChecksum {
    private UserPresetChecksum() {
    }

    public static String compute(UserMontagePresetFile fileWithoutChecksum) {
        Objects.requireNonNull(fileWithoutChecksum, "file");
        String payload = fileWithoutChecksum.schemaVersion() + "|"
                + fileWithoutChecksum.id() + "|"
                + fileWithoutChecksum.displayName() + "|"
                + fmt(fileWithoutChecksum.targetDurationSeconds()) + "|"
                + fileWithoutChecksum.aspectRatio() + "|"
                + fileWithoutChecksum.pacing() + "|"
                + fmt(fileWithoutChecksum.minimumShotDuration()) + "|"
                + fmt(fileWithoutChecksum.maximumShotDuration()) + "|"
                + fileWithoutChecksum.minimumShotCount() + "|"
                + fileWithoutChecksum.maximumShotCount() + "|"
                + fileWithoutChecksum.preferredShotTypes().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ShotType::name))
                .map(Enum::name)
                .collect(Collectors.joining(",")) + "|"
                + canonicalWeights(fileWithoutChecksum.eventWeights()) + "|"
                + canonicalTemplate(fileWithoutChecksum.introTemplate()) + "|"
                + canonicalTemplate(fileWithoutChecksum.outroTemplate()) + "|"
                + canonicalStyle(fileWithoutChecksum.style());
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

    private static String canonicalWeights(Map<String, Double> weights) {
        if (weights == null || weights.isEmpty()) return "";
        return weights.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + fmt(entry.getValue() == null ? 0.0 : entry.getValue()))
                .collect(Collectors.joining(","));
    }

    private static String canonicalTemplate(UserMontagePresetFile.Template template) {
        if (template == null) return "";
        String types = template.preferredShotTypes().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ShotType::name))
                .map(Enum::name)
                .collect(Collectors.joining(","));
        return template.id() + ";" + types + ";" + template.framing() + ";"
                + fmt(template.preferredDurationSeconds()) + ";" + fmt(template.movementIntensity());
    }

    private static String canonicalStyle(UserMontagePresetFile.Style style) {
        if (style == null) return "";
        return fmt(style.movementIntensity()) + ";"
                + fmt(style.cutFrequency()) + ";"
                + style.preferredFraming() + ";"
                + fmt(style.preferredReplaySpeed()) + ";"
                + fmt(style.minimumReplaySpeed()) + ";"
                + fmt(style.maximumReplaySpeed()) + ";"
                + fmt(style.maximumAdjacentSpeedChange()) + ";"
                + style.allowReplaySpeedChanges() + ";"
                + style.chronologicalSource() + ";"
                + style.centerSafeAreaGuide();
    }

    private static String fmt(double value) {
        if (!Double.isFinite(value)) return "nan";
        return String.format(Locale.ROOT, "%.8f", value);
    }
}
