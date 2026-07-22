package pl.peterwolf.cinewolf.visibility;

import pl.peterwolf.cinewolf.model.BoundingBox;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.model.target.GroupTarget;
import pl.peterwolf.cinewolf.model.target.StructureTarget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Pure geometric visibility analysis. World occlusion probes are optional via a caller-supplied predicate.
 */
public final class TargetVisibilityAnalyzer {
    public TargetVisibilityResult analyze(Vec3d camera, TargetPose target, double fovDegrees,
                                          BiPredicate<Vec3d, Vec3d> lineOfSightClear) {
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(target, "target");
        BiPredicate<Vec3d, Vec3d> los = lineOfSightClear == null ? (a, b) -> true : lineOfSightClear;
        List<Vec3d> probes = probePoints(target.boundingBox(), target.focusPosition());
        int clear = 0;
        List<Vec3d> occluders = new ArrayList<>();
        for (Vec3d probe : probes) {
            if (los.test(camera, probe)) clear++;
            else occluders.add(probe);
        }
        double visibility = probes.isEmpty() ? 0.0 : clear / (double) probes.size();
        double occlusion = 1.0 - visibility;
        double safeArea = safeAreaScore(camera, target.focusPosition(), fovDegrees);
        double lead = leadSpaceScore(camera, target);
        List<String> diagnostics = new ArrayList<>();
        diagnostics.add("visibility.score=" + String.format(java.util.Locale.ROOT, "%.3f", visibility));
        if (visibility >= 0.99) diagnostics.add("visibility.fully_visible");
        else if (visibility <= 0.01) diagnostics.add("visibility.fully_occluded");
        else diagnostics.add("visibility.partial");
        return new TargetVisibilityResult(visibility, occlusion, visibility >= 0.99, visibility > 0.05 && visibility < 0.99,
                visibility < 0.55, safeArea, lead, visibility, occluders, diagnostics);
    }

    public TargetVisibilityResult analyzeGroup(Vec3d camera, List<TargetPose> members, double fovDegrees,
                                               BiPredicate<Vec3d, Vec3d> lineOfSightClear) {
        if (members == null || members.isEmpty()) {
            return new TargetVisibilityResult(0.0, 1.0, false, false, true, 0.0, 0.0, 0.0,
                    List.of(), List.of("visibility.group_empty"));
        }
        int visibleMembers = 0;
        double scoreSum = 0.0;
        List<String> diagnostics = new ArrayList<>();
        for (TargetPose member : members) {
            TargetVisibilityResult result = analyze(camera, member, fovDegrees, lineOfSightClear);
            scoreSum += result.visibilityScore();
            if (result.visibilityScore() >= 0.45) visibleMembers++;
        }
        double ratio = visibleMembers / (double) members.size();
        double average = scoreSum / members.size();
        diagnostics.add("visibility.group_ratio=" + String.format(java.util.Locale.ROOT, "%.3f", ratio));
        return new TargetVisibilityResult(average, 1.0 - average, ratio >= 0.99, ratio > 0.05 && ratio < 0.99,
                ratio < 0.55, average, average, ratio, List.of(), diagnostics);
    }

    public double structureFramingDistance(StructureTarget structure, double fovDegrees) {
        Objects.requireNonNull(structure, "structure");
        double size = structure.maximumDimension();
        double halfFov = Math.toRadians(Math.max(20.0, Math.min(100.0, fovDegrees)) * 0.5);
        return Math.max(structure.suggestedFramingDistance(), (size * 0.5) / Math.tan(halfFov) * 1.15);
    }

    public GroupFramingMetrics groupMetrics(GroupTarget group, List<TargetPose> poses) {
        Objects.requireNonNull(group, "group");
        if (poses == null || poses.isEmpty()) {
            return new GroupFramingMetrics(new BoundingBox(Vec3d.ZERO, Vec3d.ZERO), Vec3d.ZERO, 0.0, Vec3d.ZERO,
                    0.0, 0.0, group.primaryMember());
        }
        Vec3d min = poses.getFirst().boundingBox().min();
        Vec3d max = poses.getFirst().boundingBox().max();
        Vec3d weighted = Vec3d.ZERO;
        Vec3d motion = Vec3d.ZERO;
        for (TargetPose pose : poses) {
            min = new Vec3d(Math.min(min.x(), pose.boundingBox().min().x()),
                    Math.min(min.y(), pose.boundingBox().min().y()),
                    Math.min(min.z(), pose.boundingBox().min().z()));
            max = new Vec3d(Math.max(max.x(), pose.boundingBox().max().x()),
                    Math.max(max.y(), pose.boundingBox().max().y()),
                    Math.max(max.z(), pose.boundingBox().max().z()));
            weighted = weighted.add(pose.focusPosition());
            motion = motion.add(pose.velocity());
        }
        BoundingBox combined = new BoundingBox(min, max);
        Vec3d center = switch (group.focusMode()) {
            case BOUNDING_BOX_CENTER -> combined.center();
            case WEIGHTED_TARGET_CENTER, EVENT_CENTER, MOTION_CENTER -> weighted.multiply(1.0 / poses.size());
            case PRIMARY_SUBJECT_BIASED -> {
                TargetPose primary = poses.stream()
                        .filter(pose -> pose.entityType().equals(group.primaryMember().entityType()))
                        .findFirst().orElse(poses.getFirst());
                yield primary.focusPosition().lerp(combined.center(), 0.35);
            }
        };
        double radius = 0.0;
        for (TargetPose pose : poses) {
            radius = Math.max(radius, center.distanceTo(pose.focusPosition()));
        }
        double spread = max.distanceTo(min);
        double stability = Math.max(0.0, 1.0 - Math.min(1.0, motion.multiply(1.0 / poses.size()).length() / 8.0));
        return new GroupFramingMetrics(combined, center, radius, motion.multiply(1.0 / poses.size()).normalizeOr(Vec3d.ZERO),
                spread, stability, group.primaryMember());
    }

    public double vehicleLeadSpaceScore(Vec3d camera, TargetPose target, Vec3d forward) {
        Vec3d toCamera = camera.subtract(target.focusPosition());
        Vec3d direction = forward == null || forward.lengthSquared() < 1.0e-9
                ? target.velocity().normalizeOr(new Vec3d(0.0, 0.0, 1.0))
                : forward.normalizeOr(new Vec3d(0.0, 0.0, 1.0));
        // Prefer space in front of the vehicle (camera looking toward travel).
        double along = -toCamera.normalizeOr(Vec3d.ZERO).dot(direction);
        return clamp01(0.5 + along * 0.5);
    }

    private static List<Vec3d> probePoints(BoundingBox box, Vec3d focus) {
        List<Vec3d> probes = new ArrayList<>(7);
        probes.add(focus);
        probes.add(box.center());
        probes.add(new Vec3d(box.min().x(), box.max().y(), box.min().z()));
        probes.add(new Vec3d(box.max().x(), box.max().y(), box.min().z()));
        probes.add(new Vec3d(box.min().x(), box.max().y(), box.max().z()));
        probes.add(new Vec3d(box.max().x(), box.max().y(), box.max().z()));
        probes.add(new Vec3d(box.center().x(), box.max().y(), box.center().z()));
        return probes;
    }

    private static double safeAreaScore(Vec3d camera, Vec3d focus, double fovDegrees) {
        double distance = camera.distanceTo(focus);
        double ideal = 8.0;
        double distanceScore = 1.0 - Math.min(1.0, Math.abs(distance - ideal) / ideal);
        double fovScore = 1.0 - Math.min(1.0, Math.abs(fovDegrees - 70.0) / 70.0);
        return clamp01(0.65 * distanceScore + 0.35 * fovScore);
    }

    private static double leadSpaceScore(Vec3d camera, TargetPose target) {
        Vec3d velocity = new Vec3d(target.velocity().x(), 0.0, target.velocity().z());
        if (velocity.lengthSquared() < 0.01) return 0.6;
        Vec3d toTarget = target.focusPosition().subtract(camera);
        Vec3d horizontal = new Vec3d(toTarget.x(), 0.0, toTarget.z()).normalizeOr(Vec3d.ZERO);
        double alignment = horizontal.dot(velocity.normalizeOr(Vec3d.ZERO));
        // Looking slightly ahead of motion scores higher.
        return clamp01(0.55 + alignment * 0.45);
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    public record GroupFramingMetrics(
            BoundingBox combinedBounds,
            Vec3d center,
            double radius,
            Vec3d movementDirection,
            double spread,
            double stability,
            pl.peterwolf.cinewolf.model.TargetReference primary
    ) {
    }
}
