package pl.peterwolf.cinewolf.montage.plan;

import pl.peterwolf.cinewolf.config.MontageShotSettings.MontageShotPreferences;
import pl.peterwolf.cinewolf.model.BoundingBox;
import pl.peterwolf.cinewolf.model.EasingType;
import pl.peterwolf.cinewolf.model.RotationDirection;
import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisResult;
import pl.peterwolf.cinewolf.montage.analysis.ReplayEntitySnapshot;
import pl.peterwolf.cinewolf.montage.event.ReplayEvent;
import pl.peterwolf.cinewolf.montage.preset.FramingType;
import pl.peterwolf.cinewolf.montage.preset.OutputAspectRatio;

import java.util.Comparator;
import java.util.Optional;

public final class DefaultShotTemplateResolver implements ShotTemplateResolver {
    @Override
    public ShotRequest createShotRequest(ReplayEvent event, TargetReference target, ShotType shotType,
                                         FramingType framing, long sourceStart, long sourceEnd,
                                         double outputDurationSeconds, double movementIntensity,
                                         int shotIndex, ReplayAnalysisResult analysis, MontageRequest request,
                                         MontagePlanningContext context) {
        MontageShotPreferences prefs = request.shotPreferences();
        double size = targetSize(target, event.peakReplayTime(), analysis).orElse(1.8);
        double framingMultiplier = switch (framing) {
            case EXTREME_WIDE -> 6.5;
            case WIDE -> 4.5;
            case MEDIUM -> 3.0;
            case CLOSE -> 1.9;
            case EXTREME_CLOSE -> 1.25;
        };
        double verticalMultiplier = request.aspectRatio() == OutputAspectRatio.VERTICAL_9_16 ? 1.2 : 1.0;
        double distance = prefs.clampDistance(size * framingMultiplier * verticalMultiplier);
        double height = prefs.clampHeight(size * (framing == FramingType.CLOSE || framing == FramingType.EXTREME_CLOSE
                ? 0.55 : 1.0));
        double orbitDiameter = prefs.clampOrbitDiameter(distance * 1.8);
        double startDistance = prefs.clampDistance(distance * 1.5);
        double endDistance = prefs.clampDistance(Math.min(distance * 0.65, startDistance));
        if (endDistance > startDistance) endDistance = startDistance;
        double rpm = clamp(0.15 + movementIntensity * 0.9, 0.05, 3.0);
        double cameraSpeed = clamp(1.5 + movementIntensity * 8.0, 0.5, 24.0);
        double fov = framing == FramingType.EXTREME_WIDE ? 78.0
                : framing == FramingType.WIDE ? 72.0
                : framing == FramingType.MEDIUM ? 65.0
                : framing == FramingType.CLOSE ? 55.0 : 45.0;
        RotationDirection direction = switch (shotType) {
            case FLYBY, SIDE_TRACKING, REVEAL, VEHICLE_PROFILE ->
                    shotIndex % 2 == 0 ? RotationDirection.LEFT_TO_RIGHT : RotationDirection.RIGHT_TO_LEFT;
            default -> shotIndex % 2 == 0 ? RotationDirection.CLOCKWISE : RotationDirection.COUNTERCLOCKWISE;
        };
        EasingType easing = request.pacing() == pl.peterwolf.cinewolf.montage.preset.MontagePacing.FAST
                ? EasingType.EASE_IN_OUT_CUBIC : EasingType.SMOOTHERSTEP;
        double resolvedDistance = switch (shotType) {
            case CLOSE_DETAIL -> prefs.clampDistance(Math.min(distance * 0.35, prefs.maximumDistance()));
            case CHASE -> prefs.clampDistance(distance * 1.1);
            case SPIRAL -> prefs.clampDistance(distance);
            default -> distance;
        };
        double resolvedStart = switch (shotType) {
            case SPIRAL -> prefs.clampDistance(orbitDiameter * 0.55);
            case CRANE_UP -> prefs.clampHeight(height * 0.4);
            case CRANE_DOWN -> prefs.clampHeight(height * 1.6);
            case REVEAL -> startDistance;
            default -> startDistance;
        };
        double resolvedEnd = switch (shotType) {
            case SPIRAL -> prefs.clampDistance(orbitDiameter * 0.25);
            case CRANE_UP -> prefs.clampHeight(height * 1.8);
            case CRANE_DOWN -> prefs.clampHeight(Math.max(prefs.minimumHeight(), height * 0.35));
            case REVEAL -> endDistance;
            default -> endDistance;
        };
        if (shotType == ShotType.REVEAL || shotType == ShotType.DOLLY_IN || shotType == ShotType.DOLLY_OUT
                || shotType == ShotType.SPIRAL) {
            if (resolvedEnd > resolvedStart && shotType != ShotType.DOLLY_OUT && shotType != ShotType.CRANE_UP) {
                // keep start farther for inward-style moves when user min/max reverse relative order
                double tmp = resolvedStart;
                resolvedStart = Math.max(resolvedStart, resolvedEnd);
                resolvedEnd = Math.min(tmp, resolvedEnd);
            }
        }
        double resolvedRpm = shotType == ShotType.SPIRAL
                ? clamp(0.25 + movementIntensity * 1.1, 0.1, 4.0)
                : rpm;
        double resolvedSpeed = switch (shotType) {
            case CHASE, SIDE_TRACKING -> clamp(cameraSpeed * 1.25, 1.0, 28.0);
            case STATIC_TRACKING -> clamp(cameraSpeed * 0.5, 0.5, 8.0);
            default -> cameraSpeed;
        };
        double lookAhead = request.aspectRatio() == OutputAspectRatio.VERTICAL_9_16
                ? Math.min(0.12, prefs.lookAheadSeconds())
                : prefs.lookAheadSeconds();
        return new ShotRequest(target, shotType, orbitDiameter, height, resolvedDistance, resolvedStart, resolvedEnd,
                resolvedRpm, outputDurationSeconds, (shotIndex * 137.5) % 360.0, direction, resolvedSpeed, fov, easing,
                lookAhead, sourceStart, sourceEnd);
    }

    private static Optional<Double> targetSize(TargetReference target, long peak, ReplayAnalysisResult analysis) {
        return analysis.samples().stream()
                .filter(sample -> sample.entities().containsKey(target))
                .min(Comparator.comparingLong(sample -> Math.abs(sample.replayTime() - peak)))
                .map(sample -> sample.entities().get(target))
                .map(ReplayEntitySnapshot::pose)
                .map(TargetPose::boundingBox)
                .map(DefaultShotTemplateResolver::maximumDimension);
    }

    private static double maximumDimension(BoundingBox box) {
        double width = box.max().x() - box.min().x();
        double height = box.max().y() - box.min().y();
        double depth = box.max().z() - box.min().z();
        return Math.max(0.5, Math.max(width, Math.max(height, depth)));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
