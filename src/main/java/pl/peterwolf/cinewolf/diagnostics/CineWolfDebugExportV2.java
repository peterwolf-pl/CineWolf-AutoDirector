package pl.peterwolf.cinewolf.diagnostics;

import pl.peterwolf.cinewolf.CineWolfAutoDirector;
import pl.peterwolf.cinewolf.project.v2.ReplayIdentity;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record CineWolfDebugExportV2(
        int schemaVersion,
        String cineWolfVersion,
        String minecraftVersion,
        String fabricLoaderVersion,
        String flashbackVersion,
        ReplayIdentity replay,
        Map<String, Object> configuration,
        Map<String, Object> sampling,
        List<Map<String, Object>> targets,
        List<Map<String, Object>> vehicles,
        List<EventDiagnostic> events,
        List<Map<String, Object>> scenes,
        Map<String, Object> montage,
        List<Map<String, Object>> shots,
        List<Map<String, Object>> collisions,
        List<Map<String, Object>> visibility,
        List<TimelineWriteDiagnostic> timelineWrites,
        List<IntegrationDiagnosticRecord> integrations,
        List<String> warnings,
        Map<String, Long> performance
) {
    public static final int SCHEMA = 2;

    public CineWolfDebugExportV2 {
        schemaVersion = schemaVersion <= 0 ? SCHEMA : schemaVersion;
        cineWolfVersion = Objects.requireNonNullElse(cineWolfVersion, CineWolfAutoDirector.VERSION);
        minecraftVersion = Objects.requireNonNullElse(minecraftVersion, "26.2");
        fabricLoaderVersion = Objects.requireNonNullElse(fabricLoaderVersion, "unknown");
        flashbackVersion = Objects.requireNonNullElse(flashbackVersion, "missing");
        configuration = Map.copyOf(configuration == null ? Map.of() : configuration);
        sampling = Map.copyOf(sampling == null ? Map.of() : sampling);
        targets = List.copyOf(targets == null ? List.of() : targets);
        vehicles = List.copyOf(vehicles == null ? List.of() : vehicles);
        events = List.copyOf(events == null ? List.of() : events);
        scenes = List.copyOf(scenes == null ? List.of() : scenes);
        montage = Map.copyOf(montage == null ? Map.of() : montage);
        shots = List.copyOf(shots == null ? List.of() : shots);
        collisions = List.copyOf(collisions == null ? List.of() : collisions);
        visibility = List.copyOf(visibility == null ? List.of() : visibility);
        timelineWrites = List.copyOf(timelineWrites == null ? List.of() : timelineWrites);
        integrations = List.copyOf(integrations == null ? List.of() : integrations);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        performance = Map.copyOf(performance == null ? Map.of() : performance);
    }
}
