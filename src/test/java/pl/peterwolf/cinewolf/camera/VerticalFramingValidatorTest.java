package pl.peterwolf.cinewolf.camera;

import org.junit.jupiter.api.Test;
import org.joml.Quaternionf;
import pl.peterwolf.cinewolf.model.BoundingBox;
import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerticalFramingValidatorTest {
    private static final TargetReference TARGET = new TargetReference(
            UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"), "minecraft:player", "Subject");

    @Test
    void acceptsDistantCenteredSubjectInsideNineBySixteenSafeArea() {
        VerticalFramingValidator.Result result = validate(camera(0.0, 1.0, -10.0, 70.0),
                pose(new BoundingBox(new Vec3d(-0.3, 0.0, -0.3), new Vec3d(0.3, 1.8, 0.3))));

        assertFalse(result.hasRisk());
        assertTrue(result.maximumFillRatio() < 1.0);
    }

    @Test
    void rejectsSubjectTooWideForVerticalSafeArea() {
        VerticalFramingValidator.Result result = validate(camera(0.0, 1.0, -3.0, 70.0),
                pose(new BoundingBox(new Vec3d(-2.0, 0.0, -0.3), new Vec3d(2.0, 2.0, 0.3))));

        assertTrue(result.hasRisk());
        assertTrue(result.maximumFillRatio() > 1.0);
    }

    @Test
    void reportsMissingPoseInsteadOfClaimingSafeFraming() {
        CameraSample sample = camera(0.0, 1.0, -10.0, 70.0);
        VerticalFramingValidator.Result result = new VerticalFramingValidator().validate(List.of(sample),
                (target, tick) -> Optional.empty(), TARGET, 9.0 / 16.0, 0.82);

        assertTrue(result.incomplete());
        assertFalse(result.hasRisk());
    }

    private static VerticalFramingValidator.Result validate(CameraSample sample, TargetPose pose) {
        return new VerticalFramingValidator().validate(List.of(sample),
                (target, tick) -> Optional.of(pose), TARGET, 9.0 / 16.0, 0.82);
    }

    private static CameraSample camera(double x, double y, double z, double fov) {
        return new CameraSample(0.0, 100L, new Vec3d(x, y, z), new Quaternionf(), 0.0, 0.0, 0.0,
                fov, new Vec3d(0.0, 1.0, 0.0), false);
    }

    private static TargetPose pose(BoundingBox box) {
        return new TargetPose(box.center(), new Vec3d(0.0, 1.0, 0.0), box, 0.0, 0.0, Vec3d.ZERO,
                "minecraft:player", false, "minecraft:overworld", false);
    }
}
