package pl.peterwolf.cinewolf.shot;

import pl.peterwolf.cinewolf.api.ShotGenerator;
import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.ReplayContext;
import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.ShotValidationResult;

public final class DollyInShotGenerator extends AbstractDollyShotGenerator implements ShotGenerator {
    @Override
    public CameraPathPlan generate(ShotRequest request, ReplayContext context) {
        return generateDolly(request, context, true);
    }

    @Override
    public ShotValidationResult validate(ShotRequest request, ReplayContext context) {
        return validateDolly(request, context, true);
    }
}
