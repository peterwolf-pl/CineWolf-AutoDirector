package pl.peterwolf.cinewolf.montage.timeline;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MontageTimelineConflictDetectorTest {
    private final MontageTimelineConflictDetector detector = new MontageTimelineConflictDetector();

    @Test
    void detectsAnEnabledSegmentSpanningTheWholeOutputIntervalWithoutKeysInside() {
        MontageTimelineConflictReport report = detector.detect(List.of(
                new MontageTimelineConflictDetector.ExistingTrack(
                        MontageTimelineConflictDetector.TrackType.CAMERA, true, List.of(90, 210))),
                new MontageTimelineInterval(100, 200));

        assertEquals(0, report.cameraKeyframes());
        assertEquals(1, report.cameraActiveSegments());
        assertTrue(report.hasConflicts());
    }

    @Test
    void disabledSegmentsAreNotActiveButStillContributeTheirLastTimelineTick() {
        MontageTimelineConflictReport report = detector.detect(List.of(
                new MontageTimelineConflictDetector.ExistingTrack(
                        MontageTimelineConflictDetector.TrackType.TIMELAPSE, false, List.of(90, 210))),
                new MontageTimelineInterval(100, 200));

        assertFalse(report.hasConflicts());
        assertEquals(210, report.lastRelevantTick());
    }
}
