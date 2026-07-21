package pl.peterwolf.cinewolf.camera;

import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.ReplayContext;
import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.ShotValidationResult;
import pl.peterwolf.cinewolf.shot.ShotGeneratorRegistry;

import java.util.List;
import java.util.Objects;

public final class CameraPathPlanner {
    private final ShotGeneratorRegistry registry;
    private final CameraPathSmoother smoother = new CameraPathSmoother();
    private final CameraPathFinalizer finalizer = new CameraPathFinalizer();

    public CameraPathPlanner(ShotGeneratorRegistry registry) {
        this.registry = Objects.requireNonNull(registry);
    }

    public ShotValidationResult validate(ShotRequest request, ReplayContext context) {
        return registry.require(request.shotType()).validate(request, context);
    }

    public CameraPathPlan generate(ShotRequest request, ReplayContext context) {
        CameraPathPlan generated = registry.require(request.shotType()).generate(request, context);
        List<CameraSample> smoothed = smoother.smooth(generated.samples(),
                context.samplingSettings().pathSmoothing());
        if (smoothed.equals(generated.samples())) return generated;
        CameraPathPlan filtered = new CameraPathPlan(generated.request(), smoothed, smoothed,
                generated.warnings(), generated.statistics());
        return finalizer.finalizePath(filtered, context.samplingSettings());
    }

    public static CameraPathPlanner createDefault() {
        return new CameraPathPlanner(ShotGeneratorRegistry.createDefault());
    }
}
