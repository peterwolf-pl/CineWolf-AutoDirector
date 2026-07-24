package pl.peterwolf.cinewolf.montage.plan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplaySourceSegmentTest {
    @Test
    void normalizesAndMergesOverlappingSegments() {
        List<ReplaySourceSegment> normalized = ReplaySourceSegment.normalize(List.of(
                new ReplaySourceSegment(100, 200, "b"),
                new ReplaySourceSegment(180, 260, "a"),
                new ReplaySourceSegment(400, 500, "c")));

        assertEquals(2, normalized.size());
        assertEquals(100, normalized.getFirst().startTick());
        assertEquals(260, normalized.getFirst().endTick());
        assertEquals(400, normalized.getLast().startTick());
        assertEquals(500, normalized.getLast().endTick());
        assertEquals(260, ReplaySourceSegment.totalDurationTicks(normalized));
    }

    @Test
    void rejectsNonForwardSegments() {
        assertThrows(IllegalArgumentException.class, () -> new ReplaySourceSegment(10, 10, "x"));
        assertThrows(IllegalArgumentException.class, () -> new ReplaySourceSegment(-1, 10, "x"));
    }

    @Test
    void containsInclusiveEndpoints() {
        ReplaySourceSegment segment = ReplaySourceSegment.of(20, 40);
        assertTrue(segment.contains(20));
        assertTrue(segment.contains(40));
        assertEquals(20, segment.durationTicks());
        assertEquals(1.0, segment.durationSeconds(), 1.0e-9);
    }
}
