package pl.peterwolf.cinewolf.api.v2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.peterwolf.cinewolf.api.ShotGenerator;
import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.montage.event.ReplayEventDetector;
import pl.peterwolf.cinewolf.montage.preset.MontagePreset;
import pl.peterwolf.cinewolf.montage.v2.MontageStyleProfile;
import pl.peterwolf.cinewolf.shot.ShotGeneratorRegistry;
import pl.peterwolf.cinewolf.vehicle.VehicleProvider;
import pl.peterwolf.cinewolf.vehicle.VehicleProviderRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Loads and isolates third-party CineWolf integrations.
 * A failing integration never disables the rest of CineWolf.
 */
public final class CineWolfIntegrationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CineWolfIntegrationManager.class);
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9_.-]{1,63}$");
    private static final Set<String> BUILTIN_PROVIDER_IDS = Set.of(
            "builtin", "soft-mod", "vanilla", "generic", "cinewolf", "orbit", "follow", "flyby"
    );

    private final VehicleProviderRegistry vehicleRegistry;
    private final ShotGeneratorRegistry shotRegistry;
    private final Map<String, IntegrationRecord> integrations = new LinkedHashMap<>();
    private final List<CinematicTargetProvider> targetProviders = new ArrayList<>();
    private final List<ReplayEventDetector> eventDetectors = new ArrayList<>();
    private final List<MontageProfileProvider> montageProfiles = new ArrayList<>();
    private final List<PresetProvider> presetProviders = new ArrayList<>();
    private final List<FramingProvider> framingProviders = new ArrayList<>();
    private final Set<String> loggedFailures = ConcurrentHashMap.newKeySet();

    public CineWolfIntegrationManager(VehicleProviderRegistry vehicleRegistry, ShotGeneratorRegistry shotRegistry) {
        this.vehicleRegistry = Objects.requireNonNull(vehicleRegistry, "vehicleRegistry");
        this.shotRegistry = Objects.requireNonNull(shotRegistry, "shotRegistry");
    }

    public static CineWolfIntegrationManager createDefault() {
        return new CineWolfIntegrationManager(
                VehicleProviderRegistry.createDefault(),
                ShotGeneratorRegistry.createDefault()
        );
    }

    public VehicleProviderRegistry vehicleRegistry() {
        return vehicleRegistry;
    }

    public ShotGeneratorRegistry shotRegistry() {
        return shotRegistry;
    }

    public synchronized IntegrationDiagnostic register(CineWolfIntegration integration) {
        Objects.requireNonNull(integration, "integration");
        String id = sanitizeId(integration.integrationId());
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (id == null) {
            errors.add("integration.invalid_id");
            return failed(integration, IntegrationStatus.FAILED, errors, warnings);
        }
        if (integrations.containsKey(id)) {
            errors.add("integration.duplicate_id");
            return failed(integration, IntegrationStatus.FAILED, errors, warnings);
        }
        CineWolfApiVersion required;
        try {
            required = integration.requiredApiVersion();
        } catch (RuntimeException exception) {
            logOnce(id, "requiredApiVersion", exception);
            errors.add("integration.api_version_error");
            return failed(integration, IntegrationStatus.FAILED, errors, warnings);
        }
        if (required == null || !CineWolfApiVersion.CURRENT.isCompatibleWith(required)) {
            errors.add("integration.incompatible_api");
            return failed(integration, IntegrationStatus.INCOMPATIBLE_API, errors, warnings);
        }

        RegistrationCounters counters = new RegistrationCounters();
        SafeRegistrationContext context = new SafeRegistrationContext(id, counters, errors, warnings);
        try {
            integration.register(context);
        } catch (RuntimeException exception) {
            logOnce(id, "register", exception);
            errors.add("integration.register_failed: " + exception.getClass().getSimpleName());
            // Keep partial registrations already validated; mark integration failed.
            IntegrationRecord record = new IntegrationRecord(integration, IntegrationStatus.FAILED,
                    counters, warnings, errors);
            integrations.put(id, record);
            return record.toDiagnostic();
        }

        IntegrationStatus status = errors.isEmpty() ? IntegrationStatus.ACTIVE : IntegrationStatus.FAILED;
        IntegrationRecord record = new IntegrationRecord(integration, status, counters, warnings, errors);
        integrations.put(id, record);
        return record.toDiagnostic();
    }

    public synchronized List<IntegrationDiagnostic> diagnostics() {
        return integrations.values().stream().map(IntegrationRecord::toDiagnostic).toList();
    }

    public synchronized List<CinematicTargetProvider> targetProviders() {
        return List.copyOf(targetProviders);
    }

    public synchronized List<ReplayEventDetector> eventDetectors() {
        return List.copyOf(eventDetectors);
    }

    public synchronized List<MontageStyleProfile> montageProfiles() {
        List<MontageStyleProfile> profiles = new ArrayList<>();
        for (MontageProfileProvider provider : montageProfiles) {
            try {
                profiles.addAll(provider.profiles());
            } catch (RuntimeException exception) {
                logOnce(provider.providerId(), "profiles", exception);
            }
        }
        return List.copyOf(profiles);
    }

    public synchronized List<MontagePreset> extraPresets() {
        List<MontagePreset> presets = new ArrayList<>();
        for (PresetProvider provider : presetProviders) {
            try {
                presets.addAll(provider.presets());
            } catch (RuntimeException exception) {
                logOnce(provider.providerId(), "presets", exception);
            }
        }
        return List.copyOf(presets);
    }

    public synchronized List<FramingProvider> framingProviders() {
        return List.copyOf(framingProviders);
    }

    public synchronized Optional<IntegrationDiagnostic> diagnostic(String integrationId) {
        IntegrationRecord record = integrations.get(integrationId);
        return record == null ? Optional.empty() : Optional.of(record.toDiagnostic());
    }

    private IntegrationDiagnostic failed(CineWolfIntegration integration, IntegrationStatus status,
                                         List<String> errors, List<String> warnings) {
        String id = sanitizeId(integration.integrationId());
        if (id == null) id = "invalid";
        IntegrationRecord record = new IntegrationRecord(integration, status, new RegistrationCounters(),
                warnings, errors);
        integrations.put(id, record);
        return record.toDiagnostic();
    }

    private void logOnce(String id, String stage, RuntimeException exception) {
        String key = id + ":" + stage;
        if (loggedFailures.add(key)) {
            LOGGER.error("CineWolf integration '{}' failed during {}: {}", id, stage, exception.toString());
        }
    }

    private static String sanitizeId(String id) {
        if (id == null) return null;
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        if (!ID_PATTERN.matcher(normalized).matches()) return null;
        if (BUILTIN_PROVIDER_IDS.contains(normalized)) return null;
        return normalized;
    }

    private final class SafeRegistrationContext implements CineWolfRegistrationContext {
        private final String integrationId;
        private final RegistrationCounters counters;
        private final List<String> errors;
        private final List<String> warnings;
        private final Set<String> claimedIds = new LinkedHashSet<>();

        private SafeRegistrationContext(String integrationId, RegistrationCounters counters,
                                        List<String> errors, List<String> warnings) {
            this.integrationId = integrationId;
            this.counters = counters;
            this.errors = errors;
            this.warnings = warnings;
        }

        @Override
        public void registerVehicleProvider(VehicleProvider provider) {
            if (!validateProvider(provider, provider == null ? null : provider.providerId())) return;
            vehicleRegistry.register(provider);
            counters.vehicles++;
        }

        @Override
        public void registerTargetProvider(CinematicTargetProvider provider) {
            if (!validateProvider(provider, provider == null ? null : provider.providerId())) return;
            targetProviders.add(provider);
            counters.targets++;
        }

        @Override
        public void registerReplayEventDetector(ReplayEventDetector detector) {
            if (!validateProvider(detector, detector == null ? null : detector.getClass().getSimpleName())) return;
            eventDetectors.add(detector);
            counters.detectors++;
        }

        @Override
        public void registerMontageProfile(MontageProfileProvider provider) {
            if (!validateProvider(provider, provider == null ? null : provider.providerId())) return;
            montageProfiles.add(provider);
            counters.montageProfiles++;
        }

        @Override
        public void registerShotGenerator(ShotType type, ShotGenerator generator) {
            if (type == null) {
                errors.add("integration.null_shot_type");
                return;
            }
            if (!validateProvider(generator, type.name().toLowerCase(Locale.ROOT))) return;
            if (shotRegistry.supports(type)) {
                warnings.add("integration.shot_override_blocked:" + type);
                return;
            }
            try {
                shotRegistry.register(type, generator);
                counters.shotGenerators++;
            } catch (RuntimeException exception) {
                errors.add("integration.shot_register_failed:" + type);
            }
        }

        @Override
        public void registerPresetProvider(PresetProvider provider) {
            if (!validateProvider(provider, provider == null ? null : provider.providerId())) return;
            presetProviders.add(provider);
            counters.presets++;
        }

        @Override
        public void registerFramingProvider(FramingProvider provider) {
            if (!validateProvider(provider, provider == null ? null : provider.providerId())) return;
            framingProviders.add(provider);
            counters.framing++;
        }

        @Override
        public String integrationId() {
            return integrationId;
        }

        @Override
        public CineWolfApiVersion apiVersion() {
            return CineWolfApiVersion.CURRENT;
        }

        private boolean validateProvider(Object provider, String providerId) {
            if (provider == null) {
                errors.add("integration.null_provider");
                return false;
            }
            String id = sanitizeId(providerId);
            if (id == null) {
                // Allow class-name style for detectors; still reject builtins and nulls.
                if (providerId == null || providerId.isBlank()) {
                    errors.add("integration.invalid_provider_id");
                    return false;
                }
                id = providerId.trim().toLowerCase(Locale.ROOT);
                if (BUILTIN_PROVIDER_IDS.contains(id)) {
                    errors.add("integration.builtin_override_blocked");
                    return false;
                }
            }
            if (!claimedIds.add(id)) {
                warnings.add("integration.duplicate_provider:" + id);
                return false;
            }
            return true;
        }
    }

    private static final class RegistrationCounters {
        int vehicles;
        int targets;
        int detectors;
        int shotGenerators;
        int montageProfiles;
        int presets;
        int framing;
    }

    private record IntegrationRecord(
            CineWolfIntegration integration,
            IntegrationStatus status,
            RegistrationCounters counters,
            List<String> warnings,
            List<String> errors
    ) {
        IntegrationDiagnostic toDiagnostic() {
            return new IntegrationDiagnostic(
                    integration.integrationId(),
                    integration.displayName(),
                    integration.integrationVersion(),
                    String.valueOf(integration.requiredApiVersion()),
                    status,
                    counters.vehicles,
                    counters.targets,
                    counters.detectors,
                    counters.shotGenerators,
                    counters.montageProfiles,
                    counters.presets,
                    counters.framing,
                    warnings,
                    errors
            );
        }
    }
}
