package pl.peterwolf.cinewolf.montage.preview;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.TestFixtures;
import pl.peterwolf.cinewolf.model.EasingType;
import pl.peterwolf.cinewolf.model.RotationDirection;
import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.montage.event.EventEvidence;
import pl.peterwolf.cinewolf.montage.event.ReplayEvent;
import pl.peterwolf.cinewolf.montage.event.ReplayEventType;
import pl.peterwolf.cinewolf.montage.plan.MontagePlan;
import pl.peterwolf.cinewolf.montage.plan.MontagePlanStatistics;
import pl.peterwolf.cinewolf.montage.plan.MontageTimeMapping;
import pl.peterwolf.cinewolf.montage.plan.MontageTransition;
import pl.peterwolf.cinewolf.montage.plan.MontageTransitionType;
import pl.peterwolf.cinewolf.montage.plan.PlannedMontageShot;
import pl.peterwolf.cinewolf.montage.preset.FramingType;
import pl.peterwolf.cinewolf.montage.preset.MontagePresetRegistry;
import pl.peterwolf.cinewolf.montage.preset.MontagePresetType;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DefaultMontagePlaybackSessionTest {
    @Test
    void supportsSeekAndShotNavigationWithoutDestroyingSession() {
        MontagePlan plan = samplePlan();
        DefaultMontagePlaybackSession session = new DefaultMontagePlaybackSession();
        assertTrue(session.enter(plan));
        assertEquals(MontagePlaybackState.READY, session.state());
        session.play();
        session.tick();
        assertTrue(session.outputSeconds() > 0.0);
        session.pause();
        assertEquals(MontagePlaybackState.PAUSED, session.state());
        session.seek(2.5);
        assertEquals(2.5, session.outputSeconds(), 1.0e-9);
        assertTrue(session.currentShotId().isPresent());
        session.nextShot();
        session.previousShot();
        session.stop();
        assertEquals(0.0, session.outputSeconds(), 1.0e-9);
        session.exit();
        assertFalse(session.active());
    }

    private static MontagePlan samplePlan() {
        var preset = MontagePresetRegistry.createDefault().get(MontagePresetType.FIFTEEN_SECONDS).orElseThrow();
        ReplayEvent event = new ReplayEvent(UUID.randomUUID(), ReplayEventType.HIGH_SPEED, 0, 10, 20,
                java.util.Set.of(TestFixtures.TARGET), Vec3d.ZERO, 0.8, 0.9,
                EventEvidence.of(EventEvidence.DetectionSource.DERIVED_MOVEMENT));
        ShotRequest request = TestFixtures.request(ShotType.FOLLOW, 0, 3, RotationDirection.CLOCKWISE, 0, 60);
        PlannedMontageShot shot1 = new PlannedMontageShot(UUID.randomUUID(), 0, event, 1.0,
                TestFixtures.TARGET, ShotType.FOLLOW, FramingType.MEDIUM, 0, 30, 0.0, 3.0, 1.0, request,
                true, false, List.of("reason"), List.of());
        ShotRequest request2 = new ShotRequest(request.target(), ShotType.ORBIT, request.diameter(), request.height(),
                request.distance(), request.startDistance(), request.endDistance(), request.rpm(), 3.0,
                request.startAngleDegrees(), RotationDirection.CLOCKWISE, request.cameraSpeed(), request.fov(),
                EasingType.LINEAR, request.lookAheadSeconds(), 30, 60);
        PlannedMontageShot shot2 = new PlannedMontageShot(UUID.randomUUID(), 1, event, 1.0,
                TestFixtures.TARGET, ShotType.ORBIT, FramingType.WIDE, 30, 60, 3.0, 3.0, 1.0, request2,
                true, false, List.of("reason"), List.of());
        return new MontagePlan(UUID.randomUUID(), preset, 0, 60, 6.0, List.of(shot1, shot2),
                List.of(new MontageTransition(shot1.shotId(), shot2.shotId(), MontageTransitionType.HARD_CUT, 3.0,
                        List.of())),
                List.of(new MontageTimeMapping(0, 3.0, 0, 30, 1.0),
                        new MontageTimeMapping(3.0, 6.0, 30, 60, 1.0)),
                new MontagePlanStatistics(2, 2, 1, 2, 1, 6.0, 0.5),
                List.of());
    }
}
