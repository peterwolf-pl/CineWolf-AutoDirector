package pl.peterwolf.cinewolf.shot;

import pl.peterwolf.cinewolf.api.ShotGenerator;
import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.ReplayContext;
import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.ShotValidationResult;
import pl.peterwolf.cinewolf.model.TargetKind;
import pl.peterwolf.cinewolf.montage.preset.FramingType;

import java.util.EnumSet;
import java.util.Set;

public final class CraneDownShotGenerator extends CraneUpShotGenerator implements ShotGenerator {
    @Override
    public CameraPathPlan generate(ShotRequest request, ReplayContext context) {
        return generateCrane(request, context, false);
    }

    @Override
    public ShotValidationResult validate(ShotRequest request, ReplayContext context) {
        return super.validate(request, context);
    }

    @Override
    public Set<TargetKind> supportedTargetKinds() {
        return EnumSet.of(TargetKind.ENTITY, TargetKind.STRUCTURE, TargetKind.AREA, TargetKind.VEHICLE, TargetKind.GROUP);
    }

    @Override
    public Set<FramingType> supportedFramingTypes() {
        return EnumSet.of(FramingType.EXTREME_WIDE, FramingType.WIDE, FramingType.MEDIUM, FramingType.CLOSE);
    }
}
