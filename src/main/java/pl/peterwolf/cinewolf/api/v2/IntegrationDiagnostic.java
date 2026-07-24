package pl.peterwolf.cinewolf.api.v2;

import java.util.List;
import java.util.Objects;

public record IntegrationDiagnostic(
        String integrationId,
        String displayName,
        String integrationVersion,
        String apiVersion,
        IntegrationStatus status,
        int registeredVehicles,
        int registeredTargets,
        int registeredEventDetectors,
        int registeredShotGenerators,
        int registeredMontageProfiles,
        int registeredPresets,
        int registeredFramingProviders,
        List<String> warnings,
        List<String> errors
) {
    public IntegrationDiagnostic {
        integrationId = Objects.requireNonNullElse(integrationId, "unknown");
        displayName = Objects.requireNonNullElse(displayName, integrationId);
        integrationVersion = Objects.requireNonNullElse(integrationVersion, "0");
        apiVersion = Objects.requireNonNullElse(apiVersion, "0.0.0");
        status = status == null ? IntegrationStatus.FAILED : status;
        registeredVehicles = Math.max(0, registeredVehicles);
        registeredTargets = Math.max(0, registeredTargets);
        registeredEventDetectors = Math.max(0, registeredEventDetectors);
        registeredShotGenerators = Math.max(0, registeredShotGenerators);
        registeredMontageProfiles = Math.max(0, registeredMontageProfiles);
        registeredPresets = Math.max(0, registeredPresets);
        registeredFramingProviders = Math.max(0, registeredFramingProviders);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        errors = List.copyOf(errors == null ? List.of() : errors);
    }
}
