package pl.peterwolf.cinewolf.montage.plan;

import pl.peterwolf.cinewolf.config.MontageShotSettings.MontageShotPreferences;
import pl.peterwolf.cinewolf.model.BoundingBox;
import pl.peterwolf.cinewolf.model.EasingType;
import pl.peterwolf.cinewolf.model.RotationDirection;
import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.montage.analysis.IndoorSceneHeuristics;
import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisResult;
import pl.peterwolf.cinewolf.montage.analysis.ReplayEntitySnapshot;
import pl.peterwolf.cinewolf.montage.event.ReplayEvent;
import pl.peterwolf.cinewolf.montage.preset.FramingType;
import pl.peterwolf.cinewolf.montage.preset.OutputAspectRatio;
import pl.peterwolf.cinewolf.montage.preset.VerticalComposition;

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
        boolean vertical = request.aspectRatio() == OutputAspectRatio.VERTICAL_9_16;
        FramingType effectiveFraming = vertical
                ? VerticalComposition.verticalSafeFraming(framing) : framing;
        double size = targetSize(target, event.peakReplayTime(), analysis).orElse(1.8);
        boolean indoor = IndoorSceneHeuristics.isLikelyIndoor(analysis, target, sourceStart, sourceEnd);
        double framingMultiplier = switch (effectiveFraming) {
            case EXTREME_WIDE -> 6.5;
            case WIDE -> 4.5;
            case MEDIUM -> 3.0;
            case CLOSE -> 1.9;
            case EXTREME_CLOSE -> 1.25;
        };
        // Indoor rooms: keep cameras close so AVOID/CLIP does not fight large outdoor framing.
        if (indoor) {
            framingMultiplier = switch (effectiveFraming) {
                case EXTREME_WIDE, WIDE -> 2.4;
                case MEDIUM -> 1.9;
                case CLOSE -> 1.35;
                case EXTREME_CLOSE -> 1.1;
            };
        }
        double verticalMultiplier = VerticalComposition.distanceMultiplier(request.aspectRatio());
        double distance = prefs.clampDistance(size * framingMultiplier * verticalMultiplier * (indoor ? 0.7 : 1.0));
        // Vertical export benefits from slightly higher camera so faces/players stay in the upper third.
        // Indoors: stay near eye height (low vertical offset).
        double heightBias = indoor ? 0.35 : (vertical ? 1.15 : 1.0);
        double height = prefs.clampHeight(size * (effectiveFraming == FramingType.CLOSE
                || effectiveFraming == FramingType.EXTREME_CLOSE ? 0.55 : 1.0) * heightBias);
        if (indoor && (shotType == ShotType.ROOM_CORNER || shotType == ShotType.STATIC_TRACKING)) {
            height = prefs.clampHeight(Math.min(height, size * 0.15));
            distance = prefs.clampDistance(Math.min(distance, Math.max(prefs.minimumDistance(), 4.5)));
        }
        double orbitDiameter = prefs.clampOrbitDiameter(distance * (vertical ? 1.55 : 1.8) * (indoor ? 0.7 : 1.0));
        double startDistance = prefs.clampDistance(distance * (indoor ? 1.15 : 1.5));
        double endDistance = prefs.clampDistance(Math.min(distance * (indoor ? 0.75 : 0.65), startDistance));
        if (endDistance > startDistance) endDistance = startDistance;
        double rpm = clamp(0.15 + movementIntensity * 0.9, 0.05, 3.0);
        // Reduce lateral speed on vertical so the subject stays centered longer.
        double cameraSpeed = clamp((1.5 + movementIntensity * 8.0) * (vertical ? 0.85 : 1.0), 0.5, 24.0);
        double fov = VerticalComposition.fovForFraming(effectiveFraming, request.aspectRatio());
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
            case STATIC_TRACKING, ROOM_CORNER, THIRD_PERSON -> clamp(cameraSpeed * 0.5, 0.5, 8.0);
            default -> cameraSpeed;
        };
        if (shotType == ShotType.THIRD_PERSON) {
            // Camera rides host head — framing distance is unused; keep tiny height for validation.
            height = prefs.clampHeight(0.0);
            resolvedDistance = prefs.clampDistance(Math.max(prefs.minimumDistance(), 1.0));
            resolvedStart = resolvedDistance;
            resolvedEnd = resolvedDistance;
        }
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
