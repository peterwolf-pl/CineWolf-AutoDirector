package pl.peterwolf.cinewolf.montage.plan;

import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisResult;
import pl.peterwolf.cinewolf.montage.event.ReplayEvent;
import pl.peterwolf.cinewolf.montage.preset.FramingType;

public interface ShotTemplateResolver {
    ShotRequest createShotRequest(ReplayEvent event, TargetReference target, ShotType shotType,
                                  FramingType framing, long sourceStart, long sourceEnd,
                                  double outputDurationSeconds, double movementIntensity,
                                  int shotIndex, ReplayAnalysisResult analysis, MontageRequest request,
                                  MontagePlanningContext context);
}
