package pl.peterwolf.cinewolf.diagnostics;

import pl.peterwolf.cinewolf.api.v2.IntegrationDiagnostic;
import pl.peterwolf.cinewolf.api.v2.IntegrationStatus;

import java.util.List;
import java.util.Objects;

public record IntegrationDiagnosticRecord(
        String providerId,
        String displayName,
        String providerVersion,
        String apiVersion,
        String status,
        int vehicles,
        int targets,
        int detectors,
        int shotGenerators,
        int montageProfiles,
        List<String> warnings,
        List<String> errors
) {
    public IntegrationDiagnosticRecord {
        providerId = Objects.requireNonNullElse(providerId, "unknown");
        displayName = Objects.requireNonNullElse(displayName, providerId);
        providerVersion = Objects.requireNonNullElse(providerVersion, "0");
        apiVersion = Objects.requireNonNullElse(apiVersion, "0.0.0");
        status = Objects.requireNonNullElse(status, IntegrationStatus.FAILED.name());
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        errors = List.copyOf(errors == null ? List.of() : errors);
    }

    public static IntegrationDiagnosticRecord from(IntegrationDiagnostic diagnostic) {
        return new IntegrationDiagnosticRecord(
                diagnostic.integrationId(),
                diagnostic.displayName(),
                diagnostic.integrationVersion(),
                diagnostic.apiVersion(),
                diagnostic.status().name(),
                diagnostic.registeredVehicles(),
                diagnostic.registeredTargets(),
                diagnostic.registeredEventDetectors(),
                diagnostic.registeredShotGenerators(),
                diagnostic.registeredMontageProfiles(),
                diagnostic.warnings(),
                diagnostic.errors()
        );
    }
}
