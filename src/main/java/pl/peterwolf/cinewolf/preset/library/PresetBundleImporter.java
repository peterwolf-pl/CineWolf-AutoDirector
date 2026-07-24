package pl.peterwolf.cinewolf.preset.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PresetBundleImporter {
    private static final Gson GSON = new GsonBuilder().create();
    private final PresetBundleValidator validator = new PresetBundleValidator();

    public ImportResult importPath(Path path) {
        Objects.requireNonNull(path, "path");
        try {
            if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                return ImportResult.failed(List.of("preset.bundle.unsafe_file"));
            }
            long size = Files.size(path);
            if (size > PresetBundleValidator.MAX_BUNDLE_BYTES) {
                return ImportResult.failed(List.of("preset.bundle.too_large"));
            }
            String json = Files.readString(path, StandardCharsets.UTF_8);
            return importJson(json, size);
        } catch (IOException exception) {
            return ImportResult.failed(List.of("preset.bundle.read_failed"));
        }
    }

    public ImportResult importJson(String json) {
        return importJson(json, json == null ? 0 : json.getBytes(StandardCharsets.UTF_8).length);
    }

    public ImportResult importJson(String json, long rawBytes) {
        if (json == null || json.isBlank()) {
            return ImportResult.failed(List.of("preset.bundle.empty_json"));
        }
        if (json.length() > PresetBundleValidator.MAX_BUNDLE_BYTES) {
            return ImportResult.failed(List.of("preset.bundle.too_large"));
        }
        // Reject obviously nested bomb-ish payloads by crude depth estimate.
        int depth = 0;
        int maxDepth = 0;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{' || c == '[') {
                depth++;
                maxDepth = Math.max(maxDepth, depth);
            } else if (c == '}' || c == ']') {
                depth = Math.max(0, depth - 1);
            }
        }
        if (maxDepth > PresetBundleValidator.MAX_NESTING_HINT) {
            return ImportResult.failed(List.of("preset.bundle.excessive_nesting"));
        }
        CineWolfPresetBundle bundle;
        try {
            bundle = GSON.fromJson(json, CineWolfPresetBundle.class);
        } catch (RuntimeException exception) {
            return ImportResult.failed(List.of("preset.bundle.parse_failed"));
        }
        PresetBundleValidator.ValidationResult validation = validator.validate(bundle, rawBytes);
        if (!validation.valid()) {
            return new ImportResult(false, null, validation.errors(), validation.warnings());
        }
        return new ImportResult(true, bundle, List.of(), validation.warnings());
    }

    public record ImportResult(boolean success, CineWolfPresetBundle bundle, List<String> errors, List<String> warnings) {
        public ImportResult {
            errors = List.copyOf(errors == null ? List.of() : errors);
            warnings = List.copyOf(warnings == null ? List.of() : warnings);
        }

        public static ImportResult failed(List<String> errors) {
            return new ImportResult(false, null, errors, List.of());
        }
    }
}
