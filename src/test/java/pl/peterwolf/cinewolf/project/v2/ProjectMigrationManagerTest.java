package pl.peterwolf.cinewolf.project.v2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProjectMigrationManagerTest {
    @TempDir Path temp;

    @Test
    void migratesInvalidJsonSafely() {
        ProjectMigrationManager manager = new ProjectMigrationManager();
        assertFalse(manager.migrateJson("").success());
        assertFalse(manager.migrateJson("{").success());
        assertFalse(manager.migrateJson("{\"schemaVersion\":99}").success());
    }

    @Test
    void migratesAlreadyV2() {
        ProjectMigrationManager manager = new ProjectMigrationManager();
        ReplayIdentity identity = new ReplayIdentity("id", "replay", 100, Instant.EPOCH, "m", "f");
        CineWolfProjectV2 project = new CineWolfProjectV2(
                2, "2.0.0", "0.41.1", UUID.randomUUID(), "Test", identity, Instant.EPOCH, Instant.EPOCH,
                null, null, null, List.of(), List.of(), List.of(),
                Set.of(), List.of(), ProjectTimelineState.empty(), ProjectUiState.defaults(),
                0, 100, List.of(), List.of()
        );
        String json = new com.google.gson.Gson().toJson(project);
        ProjectMigrationManager.MigrationResult result = manager.migrateJson(json);
        assertTrue(result.success());
        assertNotNull(result.project());
    }

    @Test
    void autosaveAndRecovery() throws Exception {
        ProjectAutosaveManager autosave = new ProjectAutosaveManager(temp.resolve("autosave"), 1);
        ReplayIdentity identity = new ReplayIdentity("id", "replay", 100, Instant.EPOCH, "m", "f");
        CineWolfProjectV2 project = new CineWolfProjectV2(
                2, "2.0.0", "0.41.1", UUID.randomUUID(), "Test", identity, Instant.EPOCH, Instant.EPOCH,
                null, null, null, List.of(), List.of(), List.of(),
                Set.of(), List.of(), ProjectTimelineState.empty(), ProjectUiState.defaults(),
                0, 100, List.of(), List.of()
        );
        autosave.markDirty(project);
        Path written = autosave.forceFlush();
        assertTrue(Files.exists(written));
        assertTrue(autosave.loadLatest().isPresent());
        ProjectRecoveryManager recovery = new ProjectRecoveryManager(temp, autosave);
        assertTrue(recovery.recover().success());
    }

    @Test
    void identityMatching() {
        ReplayIdentityResolverV2 resolver = new ReplayIdentityResolverV2();
        ReplayIdentity a = resolver.resolve("name", "meta-1", 200, Instant.EPOCH, "file.mcpr");
        ReplayIdentity b = resolver.resolve("other", "meta-1", 200, Instant.EPOCH, "other.mcpr");
        assertTrue(resolver.matches(a, b));
    }
}
