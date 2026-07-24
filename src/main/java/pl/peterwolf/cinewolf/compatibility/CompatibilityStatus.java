package pl.peterwolf.cinewolf.compatibility;

import java.util.List;
import java.util.Objects;

/** Snapshot suitable for UI status panels and diagnostics. */
public record CompatibilityStatus(
        String detectedFlashbackVersion,
        String supportedVersionRange,
        CompatibilityLevel level,
        FlashbackCapabilities capabilities,
        List<String> enabledFeatures,
        List<String> disabledFeatures,
        List<String> integrationMethods,
        List<String> warnings,
        String cineWolfVersion,
        boolean editorIntegrationEnabled
) {
    public CompatibilityStatus {
        supportedVersionRange = Objects.requireNonNullElse(supportedVersionRange, "none");
        level = level == null ? CompatibilityLevel.MISSING : level;
        capabilities = capabilities == null ? FlashbackCapabilities.none() : capabilities;
        enabledFeatures = List.copyOf(enabledFeatures == null ? List.of() : enabledFeatures);
        disabledFeatures = List.copyOf(disabledFeatures == null ? List.of() : disabledFeatures);
        integrationMethods = List.copyOf(integrationMethods == null ? List.of() : integrationMethods);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        cineWolfVersion = Objects.requireNonNullElse(cineWolfVersion, "unknown");
    }

    public static CompatibilityStatus from(FlashbackCompatibilityRegistry.CompatibilityAssessment assessment) {
        Objects.requireNonNull(assessment, "assessment");
        return new CompatibilityStatus(
                assessment.detectedVersion(),
                assessment.rule().supportedVersions().display(),
                assessment.level(),
                assessment.capabilities(),
                List.copyOf(assessment.rule().enabledFeatures()),
                List.copyOf(assessment.rule().disabledFeatures()),
                List.copyOf(assessment.rule().integrationMethods()),
                List.copyOf(assessment.rule().warnings()),
                assessment.cineWolfVersion(),
                assessment.editorIntegrationEnabled()
        );
    }
}
