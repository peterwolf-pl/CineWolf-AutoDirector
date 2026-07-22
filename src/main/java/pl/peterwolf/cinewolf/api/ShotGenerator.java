package pl.peterwolf.cinewolf.api;

import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.ReplayContext;
import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.ShotValidationResult;
import pl.peterwolf.cinewolf.model.TargetKind;
import pl.peterwolf.cinewolf.montage.preset.FramingType;
import pl.peterwolf.cinewolf.shot.ShotCapabilities;
import pl.peterwolf.cinewolf.shot.ShotParameterSchema;

import java.util.EnumSet;
import java.util.Set;

public interface ShotGenerator {
    CameraPathPlan generate(ShotRequest request, ReplayContext context);

    ShotValidationResult validate(ShotRequest request, ReplayContext context);

    default Set<TargetKind> supportedTargetKinds() {
        return EnumSet.of(TargetKind.ENTITY, TargetKind.VEHICLE);
    }

    default Set<FramingType> supportedFramingTypes() {
        return EnumSet.allOf(FramingType.class);
    }

    default ShotCapabilities capabilities() {
        return ShotCapabilities.basicDynamic();
    }

    default ShotParameterSchema parameterSchema() {
        return ShotParameterSchema.of(
                new ShotParameterSchema.Parameter("distance", "number", 1.0, 128.0, 8.0),
                new ShotParameterSchema.Parameter("height", "number", -32.0, 64.0, 3.0),
                new ShotParameterSchema.Parameter("fov", "number", 1.0, 110.0, 70.0)
        );
    }
}
