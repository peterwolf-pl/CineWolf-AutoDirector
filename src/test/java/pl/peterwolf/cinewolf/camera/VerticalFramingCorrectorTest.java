package pl.peterwolf.cinewolf.camera;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.TestFixtures;
import pl.peterwolf.cinewolf.model.BoundingBox;
import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.montage.preset.VerticalComposition;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class VerticalFramingCorrectorTest {
    @Test
    void pullsBackWhenSubjectFillsHorizontalFrame() {
        // Camera very close to a wide box — 9:16 projection should be outside.
        Vec3d camera = new Vec3d(0, 1.5, 0.4);
        Vec3d look = new Vec3d(0, 1.5, 2.0);
        TargetPose pose = new TargetPose(look, look,
                new BoundingBox(new Vec3d(-1.2, 0, 1.5), new Vec3d(1.2, 2.0, 2.5)),
                0, 0, Vec3d.ZERO, "minecraft:player", false, "minecraft:overworld", false);
        CameraLookAtSolver.Orientation orientation = new CameraLookAtSolver().solve(camera, look, 0);
        CameraSample sample = new CameraSample(0, 0, camera, orientation.quaternion(),
                orientation.yaw(), orientation.pitch(), 0, 70, look, false, false);

        VerticalFramingCorrector corrector = new VerticalFramingCorrector();
        var result = corrector.correct(List.of(sample),
                (target, tick) -> Optional.of(pose),
                TestFixtures.TARGET,
                VerticalComposition.WIDTH_TO_HEIGHT,
                0.82);

        assertFalse(result.samples().isEmpty());
        double originalDistance = camera.distanceTo(look);
        double correctedDistance = result.samples().getFirst().position().distanceTo(look);
        assertTrue(correctedDistance + 1.0e-6 >= originalDistance);
    }

    @Test
    void leavesSafeSamplesUnchanged() {
        Vec3d camera = new Vec3d(0, 2, -12);
        Vec3d look = new Vec3d(0, 1.5, 0);
        TargetPose pose = TestFixtures.pose(look, Vec3d.ZERO, 0);
        CameraLookAtSolver.Orientation orientation = new CameraLookAtSolver().solve(camera, look, 0);
        CameraSample sample = new CameraSample(0, 0, camera, orientation.quaternion(),
                orientation.yaw(), orientation.pitch(), 0, 55, look, false, false);

        VerticalFramingCorrector corrector = new VerticalFramingCorrector();
        var result = corrector.correct(List.of(sample),
                (target, tick) -> Optional.of(pose),
                TestFixtures.TARGET,
                VerticalComposition.WIDTH_TO_HEIGHT,
                0.82);
        assertEquals(0, result.adjustedSamples());
        assertEquals(camera, result.samples().getFirst().position());
    }
}
