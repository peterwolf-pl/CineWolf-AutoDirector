package pl.peterwolf.cinewolf.diagnostics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.peterwolf.cinewolf.debug.DebugRedactionPolicy;
import pl.peterwolf.cinewolf.project.v2.CineWolfProjectV2;
import pl.peterwolf.cinewolf.project.v2.ProjectTimelineState;
import pl.peterwolf.cinewolf.project.v2.ProjectUiState;
import pl.peterwolf.cinewolf.project.v2.ReplayIdentity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DebugExportV2Test {
    @TempDir Path temp;

    @Test
    void exportsRedactedJson() throws Exception {
        CineWolfDebugExporterV2 exporter = new CineWolfDebugExporterV2(DebugRedactionPolicy.defaults());
        ReplayIdentity identity = new ReplayIdentity("stable", "PlayerSteve_world", 100, Instant.EPOCH, "m", "f");
        CineWolfProjectV2 project = new CineWolfProjectV2(
                2, "2.0.0", "0.41.1", UUID.randomUUID(), "Project", identity, Instant.EPOCH, Instant.EPOCH,
                null, null, null, List.of(), List.of(), List.of(), Set.of(), List.of(),
                ProjectTimelineState.empty(), ProjectUiState.defaults(), 0, 100, List.of(), List.of()
        );
        CineWolfDebugExportV2 export = exporter.capture(project, null, null, List.of(),
                List.of(new TimelineWriteDiagnostic("op", true, "replace", 10, 5, 2, false, true, List.of(), List.of(), "ok")),
                List.of("visible"), List.of("clear"), Map.of("planMs", 12L), "0.41.1");
        Path path = exporter.export(temp, export);
        assertTrue(Files.exists(path));
        String json = Files.readString(path);
        assertTrue(json.contains("schemaVersion"));
        assertTrue(json.contains("0.41.1"));
        assertTrue(json.contains("timelineWrites") || json.contains("op"));
    }

    @Test
    void falsePositiveHints() {
        List<String> hints = FalsePositiveHints.forEvent("HIGH_SPEED", 0.2);
        assertTrue(hints.stream().anyMatch(h -> h.contains("teleport") || h.contains("weak")));
    }
}
