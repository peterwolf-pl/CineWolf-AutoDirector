package pl.peterwolf.cinewolf.preset.library;

import pl.peterwolf.cinewolf.CineWolfAutoDirector;
import pl.peterwolf.cinewolf.compatibility.VersionRange;
import pl.peterwolf.cinewolf.preset.user.UserMontagePresetFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Security-focused validation for local preset bundles. */
public final class PresetBundleValidator {
    public static final long MAX_BUNDLE_BYTES = 1024L * 1024L;
    public static final int MAX_PRESETS = 64;
    public static final int MAX_STRING_LENGTH = 2048;
    public static final int MAX_NESTING_HINT = 32;

    private static final Pattern SAFE_ID = Pattern.compile("^[a-z0-9][a-z0-9_.-]{1,63}$");
    private static final Pattern DANGEROUS = Pattern.compile(
            "(?i)(javascript:|data:text/html|Runtime\\.getRuntime|ProcessBuilder|Class\\.forName|\\\\u0000)");

    public ValidationResult validate(CineWolfPresetBundle bundle, long rawBytes) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (bundle == null) {
            errors.add("preset.bundle.null");
            return new ValidationResult(false, errors, warnings);
        }
        if (rawBytes > MAX_BUNDLE_BYTES) errors.add("preset.bundle.too_large");
        if (bundle.schemaVersion() != CineWolfPresetBundle.CURRENT_SCHEMA) {
            errors.add("preset.bundle.unsupported_schema:" + bundle.schemaVersion());
        }
        if (!SAFE_ID.matcher(bundle.bundleId().toLowerCase(Locale.ROOT)).matches()) {
            errors.add("preset.bundle.invalid_id");
        }
        rejectDangerous(bundle.displayName(), "displayName", errors);
        rejectDangerous(bundle.description(), "description", errors);
        rejectDangerous(bundle.author(), "author", errors);
        rejectDangerous(bundle.license(), "license", errors);
        if (bundle.montagePresets().size() + bundle.shotPresets().size() > MAX_PRESETS) {
            errors.add("preset.bundle.too_many_presets");
        }
        if (bundle.montagePresets().isEmpty() && bundle.shotPresets().isEmpty()) {
            errors.add("preset.bundle.empty");
        }
        if (VersionRange.compare(CineWolfAutoDirector.VERSION, bundle.minimumCineWolfVersion()) < 0) {
            warnings.add("preset.bundle.cinewolf_version_low");
        }
        Set<String> ids = new HashSet<>();
        for (UserMontagePresetFile preset : bundle.montagePresets()) {
            if (preset == null) {
                errors.add("preset.bundle.null_montage_preset");
                continue;
            }
            if (!ids.add(preset.id())) errors.add("preset.bundle.duplicate_id:" + preset.id());
            rejectDangerous(preset.displayName(), "montage.displayName", errors);
            rejectDangerous(preset.description(), "montage.description", errors);
            if (containsPathTraversal(preset.id()) || containsPathTraversal(preset.displayName())) {
                errors.add("preset.bundle.path_traversal");
            }
        }
        for (UserShotPresetFile preset : bundle.shotPresets()) {
            if (preset == null) {
                errors.add("preset.bundle.null_shot_preset");
                continue;
            }
            if (!ids.add("shot:" + preset.id())) errors.add("preset.bundle.duplicate_shot_id:" + preset.id());
            for (String value : preset.stringParameters().values()) {
                rejectDangerous(value, "shot.param", errors);
                if (looksLikePath(value)) errors.add("preset.bundle.filesystem_path_forbidden");
            }
            for (Double number : preset.numericParameters().values()) {
                if (number == null || !Double.isFinite(number) || Math.abs(number) > 1_000_000.0) {
                    errors.add("preset.bundle.numeric_limit");
                }
            }
        }
        if (!bundle.requiredIntegrationIds().isEmpty()) {
            warnings.add("preset.bundle.requires_integrations:" + bundle.requiredIntegrationIds().size());
        }
        if (bundle.checksum() != null && !bundle.checksum().isBlank()) {
            String expected = computeChecksum(bundle);
            if (!expected.equalsIgnoreCase(bundle.checksum())) {
                errors.add("preset.bundle.checksum_mismatch");
            }
        }
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    public String computeChecksum(CineWolfPresetBundle bundle) {
        CineWolfPresetBundle normalized = new CineWolfPresetBundle(
                bundle.schemaVersion(), bundle.bundleId(), bundle.displayName(), bundle.description(),
                bundle.author(), bundle.license(), bundle.minimumCineWolfVersion(),
                bundle.minimumFlashbackVersion(), bundle.requiredIntegrationIds(),
                bundle.montagePresets(), bundle.shotPresets(), bundle.metadata(), "");
        String payload = normalized.bundleId() + "|" + normalized.montagePresets().size()
                + "|" + normalized.shotPresets().size() + "|" + normalized.author();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)))
                    .toLowerCase(Locale.ROOT);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to compute bundle checksum", exception);
        }
    }

    private static void rejectDangerous(String value, String field, List<String> errors) {
        if (value == null) return;
        if (value.length() > MAX_STRING_LENGTH) errors.add("preset.bundle.string_too_long:" + field);
        if (DANGEROUS.matcher(value).find()) errors.add("preset.bundle.dangerous_content:" + field);
    }

    private static boolean containsPathTraversal(String value) {
        if (value == null) return false;
        return value.contains("..") || value.contains("/") || value.contains("\\");
    }

    private static boolean looksLikePath(String value) {
        if (value == null) return false;
        return value.startsWith("/") || value.matches("(?i)[a-z]:\\\\.*") || value.contains("..");
    }

    public record ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
        public ValidationResult {
            errors = List.copyOf(errors == null ? List.of() : errors);
            warnings = List.copyOf(warnings == null ? List.of() : warnings);
        }
    }
}
