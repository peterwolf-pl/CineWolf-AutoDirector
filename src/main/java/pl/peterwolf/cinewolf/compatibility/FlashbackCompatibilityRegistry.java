package pl.peterwolf.cinewolf.compatibility;

import pl.peterwolf.cinewolf.CineWolfAutoDirector;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Versioned Flashback compatibility table.
 * Unsupported versions never crash the game; risky integrations stay disabled.
 */
public final class FlashbackCompatibilityRegistry {
    /** Recommended / compile-time Flashback pin. */
    public static final String SUPPORTED_VERSION = "0.43.4";
    /** Oldest Flashback build with full editor integration. */
    public static final String MIN_SUPPORTED_VERSION = "0.42.1";
    /** First major line that is not treated as a compatible 0.x editor. */
    public static final String NEXT_UNSUPPORTED_MAJOR = "1.0.0";
    public static final VersionRange SUPPORTED_RANGE = VersionRange.atLeast(MIN_SUPPORTED_VERSION);

    private static final List<String> BASELINE_METHODS = List.of(
            "public_flashback_classes",
            "fabric_client_events",
            "narrow_mixin_accessors",
            "rendering_mixin_host",
            "cinewolf_owned_timeline_overlay"
    );

    private FlashbackCompatibilityRegistry() {
    }

    public static boolean isFullySupported(String version) {
        if (version == null || version.isBlank()) return false;
        String trimmed = version.trim();
        return VersionRange.compare(trimmed, MIN_SUPPORTED_VERSION) >= 0
                && VersionRange.compare(trimmed, NEXT_UNSUPPORTED_MAJOR) < 0;
    }

    public static CompatibilityAssessment assess(Optional<String> detectedVersion) {
        Objects.requireNonNull(detectedVersion, "detectedVersion");
        if (detectedVersion.isEmpty()) {
            FlashbackCapabilities caps = FlashbackCapabilities.none();
            FlashbackCompatibilityRule rule = new FlashbackCompatibilityRule(
                    SUPPORTED_RANGE,
                    CompatibilityLevel.MISSING,
                    caps.enabledFeatures(),
                    caps.disabledFeatures(),
                    List.of("compatibility.flashback_missing"),
                    List.of()
            );
            return new CompatibilityAssessment(null, rule, caps, CineWolfAutoDirector.VERSION, false);
        }

        String version = detectedVersion.get().trim();
        if (isFullySupported(version)) {
            FlashbackCapabilities caps = FlashbackCapabilities.flashback0434();
            FlashbackCompatibilityRule rule = new FlashbackCompatibilityRule(
                    SUPPORTED_RANGE,
                    CompatibilityLevel.SUPPORTED,
                    caps.enabledFeatures(),
                    caps.disabledFeatures(),
                    List.of(),
                    BASELINE_METHODS
            );
            return new CompatibilityAssessment(version, rule, caps, CineWolfAutoDirector.VERSION, true);
        }

        // 0.42.0 is adjacent to the supported floor but was never the validated pin.
        if (version.startsWith("0.42.")) {
            FlashbackCapabilities caps = FlashbackCapabilities.flashback0434();
            List<String> warnings = new ArrayList<>();
            warnings.add("compatibility.flashback_unvalidated_patch");
            warnings.add("compatibility.risky_mixins_disabled");
            FlashbackCompatibilityRule rule = new FlashbackCompatibilityRule(
                    SUPPORTED_RANGE,
                    CompatibilityLevel.EXPERIMENTAL,
                    caps.enabledFeatures(),
                    caps.disabledFeatures(),
                    warnings,
                    List.of("public_flashback_classes", "cinewolf_owned_timeline_overlay")
            );
            return new CompatibilityAssessment(version, rule, caps, CineWolfAutoDirector.VERSION, false);
        }

        // Previous validated line (0.41.x) remains usable with experimental/off mixins policy.
        if (version.startsWith("0.41.")) {
            FlashbackCapabilities caps = FlashbackCapabilities.flashback0434();
            List<String> warnings = new ArrayList<>();
            warnings.add("compatibility.flashback_legacy_line");
            warnings.add("compatibility.flashback_unvalidated_patch");
            warnings.add("compatibility.risky_mixins_disabled");
            FlashbackCompatibilityRule rule = new FlashbackCompatibilityRule(
                    SUPPORTED_RANGE,
                    CompatibilityLevel.EXPERIMENTAL,
                    caps.enabledFeatures(),
                    caps.disabledFeatures(),
                    warnings,
                    List.of("public_flashback_classes", "cinewolf_owned_timeline_overlay")
            );
            return new CompatibilityAssessment(version, rule, caps, CineWolfAutoDirector.VERSION, false);
        }

        FlashbackCapabilities caps = FlashbackCapabilities.none();
        FlashbackCompatibilityRule rule = new FlashbackCompatibilityRule(
                SUPPORTED_RANGE,
                CompatibilityLevel.UNSUPPORTED,
                caps.enabledFeatures(),
                caps.disabledFeatures(),
                List.of("compatibility.flashback_unsupported",
                        "compatibility.supported_range_" + MIN_SUPPORTED_VERSION),
                List.of()
        );
        return new CompatibilityAssessment(version, rule, caps, CineWolfAutoDirector.VERSION, false);
    }

    public record CompatibilityAssessment(
            String detectedVersion,
            FlashbackCompatibilityRule rule,
            FlashbackCapabilities capabilities,
            String cineWolfVersion,
            boolean editorIntegrationEnabled
    ) {
        public CompatibilityLevel level() {
            return rule.level();
        }

        public String failureMessage() {
            if (detectedVersion == null) {
                return "Flashback is not installed; CineWolf editor integration is disabled";
            }
            return "Detected Flashback " + detectedVersion + "; CineWolf " + cineWolfVersion
                    + " supports Flashback " + MIN_SUPPORTED_VERSION + "+ (recommended " + SUPPORTED_VERSION + ")";
        }
    }
}
