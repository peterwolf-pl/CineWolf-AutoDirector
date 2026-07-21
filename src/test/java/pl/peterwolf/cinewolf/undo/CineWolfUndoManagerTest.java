package pl.peterwolf.cinewolf.undo;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CineWolfUndoManagerTest {
    @Test
    void restoresOneOperationSnapshotOnlyOnce() {
        CineWolfUndoManager<Map<Integer, String>> manager = new CineWolfUndoManager<>();
        Map<Integer, String> timelineBefore = Map.of(10, "user-camera", 20, "user-fov");
        manager.remember(new HashMap<>(timelineBefore));
        Map<Integer, String> restored = manager.take().orElseThrow();
        assertEquals(timelineBefore, restored);
        assertTrue(manager.take().isEmpty());
    }

    @Test
    void newestOperationReplacesOlderSnapshot() {
        CineWolfUndoManager<String> manager = new CineWolfUndoManager<>();
        manager.remember("first");
        manager.remember("second");
        assertEquals("second", manager.take().orElseThrow());
    }
}
