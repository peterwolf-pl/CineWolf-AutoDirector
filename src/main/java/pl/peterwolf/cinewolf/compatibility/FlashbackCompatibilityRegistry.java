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
    public static final String SUPPORTED_VERSION = "0.41.1";
    public static final VersionRange SUPPORTED_RANGE = VersionRange.exact(SUPPORTED_VERSION);

    private static final List<String> BASELINE_METHODS = List.of(
            "public_flashback_classes",
            "fabric_client_events",
            "narrow_mixin_accessors",
            "rendering_mixin_host",
            "cinewolf_owned_timeline_overlay"
    );

    private FlashbackCompatibilityRegistry() {
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
        if (SUPPORTED_VERSION.equals(version)) {
            FlashbackCapabilities caps = FlashbackCapabilities.flashback0411();
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

        // Nearby 0.41.x builds are experimental until validated.
        if (version.startsWith("0.41.")) {
            FlashbackCapabilities caps = FlashbackCapabilities.flashback0411();
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

        FlashbackCapabilities caps = FlashbackCapabilities.none();
        FlashbackCompatibilityRule rule = new FlashbackCompatibilityRule(
                SUPPORTED_RANGE,
                CompatibilityLevel.UNSUPPORTED,
                caps.enabledFeatures(),
                caps.disabledFeatures(),
                List.of("compatibility.flashback_unsupported",
                        "compatibility.supported_exactly_" + SUPPORTED_VERSION),
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
                    + " supports " + SUPPORTED_VERSION;
        }
    }
}
