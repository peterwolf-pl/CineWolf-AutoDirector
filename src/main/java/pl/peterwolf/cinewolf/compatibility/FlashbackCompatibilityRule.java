package pl.peterwolf.cinewolf.compatibility;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record FlashbackCompatibilityRule(
        VersionRange supportedVersions,
        CompatibilityLevel level,
        Set<String> enabledFeatures,
        Set<String> disabledFeatures,
        List<String> warnings,
        List<String> integrationMethods
) {
    public FlashbackCompatibilityRule {
        Objects.requireNonNull(supportedVersions, "supportedVersions");
        level = level == null ? CompatibilityLevel.UNSUPPORTED : level;
        enabledFeatures = Set.copyOf(enabledFeatures == null ? Set.of() : enabledFeatures);
        disabledFeatures = Set.copyOf(disabledFeatures == null ? Set.of() : disabledFeatures);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        integrationMethods = List.copyOf(integrationMethods == null ? List.of() : integrationMethods);
    }
}
