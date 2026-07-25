package pl.peterwolf.cinewolf.montage.highlight;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.peterwolf.cinewolf.montage.event.detector.ReplayMarkerEventDetector;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MontageHighlightTest {
    @TempDir
    Path temp;

    @Test
    void momentPadsAroundTick() {
        MontageHighlight moment = MontageHighlight.moment(100, "x", 30);
        assertEquals(70, moment.startTick());
        assertEquals(130, moment.endTick());
        assertEquals(MontageHighlight.Kind.MOMENT, moment.kind());
    }

    @Test
    void userHighlightLabelsMatchHotkeyMarkers() {
        assertTrue(ReplayMarkerEventDetector.isUserHighlightLabel("CineWolf: moment@120"));
        assertTrue(ReplayMarkerEventDetector.isUserHighlightLabel("CineWolf fragment start"));
        assertTrue(ReplayMarkerEventDetector.isUserHighlightLabel("moment@40"));
        assertTrue(ReplayMarkerEventDetector.isUserHighlightLabel("fragment"));
        assertTrue(ReplayMarkerEventDetector.isUserHighlightLabel("cinewolf-moment"));
        assertFalse(ReplayMarkerEventDetector.isUserHighlightLabel("player death"));
        assertFalse(ReplayMarkerEventDetector.isUserHighlightLabel(""));
    }

    @Test
    void fragmentToggleCompletesOnSecondPress() {
        MontageHighlightStore store = new MontageHighlightStore(temp.resolve("h.json"),
                org.slf4j.LoggerFactory.getLogger("test"));
        store.setActiveReplay(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        Optional<MontageHighlight> first = store.toggleFragment(20, "f");
        assertTrue(first.isEmpty());
        assertEquals(20L, store.pendingFragmentStartTick());

        Optional<MontageHighlight> second = store.toggleFragment(120, "f");
        assertTrue(second.isPresent());
        assertEquals(20, second.get().startTick());
        assertEquals(120, second.get().endTick());
        assertEquals(1, store.highlightsForActiveReplay().size());
    }

    @Test
    void persistsAndReloadsPerReplay() {
        Path path = temp.resolve("store.json");
        UUID replay = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        MontageHighlightStore first = new MontageHighlightStore(path, org.slf4j.LoggerFactory.getLogger("test"));
        first.setActiveReplay(replay);
        first.addMoment(40, "m", 10);

        MontageHighlightStore second = new MontageHighlightStore(path, org.slf4j.LoggerFactory.getLogger("test"));
        List<MontageHighlight> loaded = second.highlightsFor(replay.toString());
        assertEquals(1, loaded.size());
        assertEquals(30, loaded.getFirst().startTick());
        assertEquals(50, loaded.getFirst().endTick());
    }
}
