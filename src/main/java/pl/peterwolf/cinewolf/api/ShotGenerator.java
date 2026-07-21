package pl.peterwolf.cinewolf.api;

import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.ReplayContext;
import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.ShotValidationResult;

public interface ShotGenerator {
    CameraPathPlan generate(ShotRequest request, ReplayContext context);

    ShotValidationResult validate(ShotRequest request, ReplayContext context);
}
