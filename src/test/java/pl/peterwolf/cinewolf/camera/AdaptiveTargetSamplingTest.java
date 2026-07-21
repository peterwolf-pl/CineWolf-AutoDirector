package pl.peterwolf.cinewolf.camera;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.TestFixtures;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdaptiveTargetSamplingTest {
    private final AdaptiveTargetSampling sampling = new AdaptiveTargetSampling();

    @Test
    void addsMidpointsAroundSharpTurn() {
        Map<Long, TargetPose> poses = Map.of(
                0L, TestFixtures.pose(new Vec3d(0, 0, 0), Vec3d.ZERO, 0),
                4L, TestFixtures.pose(new Vec3d(4, 0, 0), Vec3d.ZERO, 0),
                8L, TestFixtures.pose(new Vec3d(4, 0, 4), Vec3d.ZERO, 0));
        assertEquals(List.of(2L, 6L), sampling.selectAdditionalTicks(poses, List.of(0L, 4L, 8L), 8));
    }

    @Test
    void keepsStraightConstantMotionAtBaseRate() {
        Map<Long, TargetPose> poses = Map.of(
                0L, TestFixtures.pose(new Vec3d(0, 0, 0), Vec3d.ZERO, 0),
                4L, TestFixtures.pose(new Vec3d(4, 0, 0), Vec3d.ZERO, 0),
                8L, TestFixtures.pose(new Vec3d(8, 0, 0), Vec3d.ZERO, 0));
        assertEquals(List.of(), sampling.selectAdditionalTicks(poses, List.of(0L, 4L, 8L), 8));
    }

    @Test
    void obeysAdditionalSampleLimit() {
        Map<Long, TargetPose> poses = Map.of(
                0L, TestFixtures.pose(new Vec3d(0, 0, 0), Vec3d.ZERO, 0),
                4L, TestFixtures.pose(new Vec3d(30, 0, 0), Vec3d.ZERO, 0),
                8L, TestFixtures.pose(new Vec3d(60, 0, 0), Vec3d.ZERO, 0));
        assertEquals(1, sampling.selectAdditionalTicks(poses, List.of(0L, 4L, 8L), 1).size());
    }
}
