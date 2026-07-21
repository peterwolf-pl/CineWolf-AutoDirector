package pl.peterwolf.cinewolf.montage.timeline;

import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.montage.plan.MontageTimeMapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Builds the complete native-keyframe payload before any Flashback state is mutated. */
public final class MontageTimelinePlanBuilder {
    private static final double TICKS_PER_SECOND = 20.0;
    private static final double TIME_EPSILON = 1.0e-6;
    private static final double SPEED_EPSILON = 1.0e-5;
    private static final double FOV_SIMPLIFICATION_EPSILON = 0.05;

    public BuildResult build(MontageTimelineWriteRequest request) {
        if (request == null) {
            return new BuildResult(Optional.empty(), List.of("montage.timeline.request_missing"), List.of());
        }

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (request.absoluteOutputStartTick() < 0 || request.absoluteOutputStartTick() > Integer.MAX_VALUE) {
            errors.add("montage.timeline.output_start_out_of_range");
        }
        if (request.keyframeLimit() <= 0) errors.add("montage.timeline.invalid_keyframe_limit");
        if (request.generatedShots().isEmpty()) errors.add("montage.timeline.shots_empty");
        if (request.timeMappings().isEmpty()) errors.add("montage.timeline.mappings_empty");

        List<MontageGeneratedShot> shots = request.generatedShots().stream()
                .sorted(Comparator.comparingDouble(MontageGeneratedShot::outputStartSeconds))
                .toList();
        if (!shots.equals(request.generatedShots())) warnings.add("montage.timeline.shots_reordered");

        List<ShotPayload> shotPayloads = new ArrayList<>();
        Set<Integer> forcedFovTicks = new HashSet<>();
        double previousShotEnd = Double.NaN;
        double lastShotEnd = 0.0;
        for (MontageGeneratedShot shot : shots) {
            CameraPathPlan path = shot.path();
            if (!Double.isFinite(shot.outputStartSeconds()) || shot.outputStartSeconds() < 0.0) {
                errors.add("montage.timeline.shot_output_start_invalid");
                continue;
            }
            if (!path.valid()) errors.add("montage.timeline.camera_path_invalid");
            List<CameraSample> samples = path.simplifiedSamples().isEmpty()
                    ? path.samples() : path.simplifiedSamples();
            if (samples.size() < 2) {
                errors.add("montage.timeline.shot_requires_two_samples");
                continue;
            }

            TreeMap<Integer, MontageTimelineWritePlan.CameraPoint> shotCamera = new TreeMap<>();
            TreeMap<Integer, MontageTimelineWritePlan.FovPoint> shotFov = new TreeMap<>();
            double previousCinematicTime = Double.NEGATIVE_INFINITY;
            long previousReplayTime = -1L;
            for (CameraSample sample : samples) {
                if (!sample.isFinite() || sample.cinematicTimeSeconds() < 0.0) {
                    errors.add("montage.timeline.camera_sample_invalid");
                    continue;
                }
                if (sample.cinematicTimeSeconds() + TIME_EPSILON < previousCinematicTime) {
                    errors.add("montage.timeline.camera_samples_not_ordered");
                }
                previousCinematicTime = sample.cinematicTimeSeconds();
                if (sample.replayTime() < 0 || sample.replayTime() > Integer.MAX_VALUE) {
                    errors.add("montage.timeline.source_time_out_of_range");
                    continue;
                }
                if (previousReplayTime >= 0 && sample.replayTime() <= previousReplayTime) {
                    errors.add("montage.timeline.camera_source_not_strictly_increasing");
                }
                previousReplayTime = sample.replayTime();
                if (sample.fov() <= 0.0 || sample.fov() >= 180.0) {
                    errors.add("montage.timeline.fov_out_of_range");
                }

                int sourceTick = (int) sample.replayTime();
                MontageTimelineWritePlan.CameraPoint previousCamera = shotCamera.put(sourceTick,
                        new MontageTimelineWritePlan.CameraPoint(sourceTick, sample.position(), sample.yaw(),
                                sample.pitch(), sample.roll(), path.request().easing()));
                shotFov.put(sourceTick,
                        new MontageTimelineWritePlan.FovPoint(sourceTick, sample.fov(), path.request().easing()));
                if (previousCamera != null) warnings.add("montage.timeline.camera_ticks_collapsed");
                if (sample.discontinuity()) forcedFovTicks.add(sourceTick);
            }

            if (shotCamera.size() < 2) errors.add("montage.timeline.shot_collapsed_to_one_tick");
            if (!shotCamera.isEmpty()) {
                forcedFovTicks.add(shotCamera.firstKey());
                forcedFovTicks.add(shotCamera.lastKey());
                shotPayloads.add(new ShotPayload(shotCamera, shotFov));
            }

            double shotEnd = shot.outputStartSeconds() + Math.max(0.0, previousCinematicTime);
            if (Double.isFinite(previousShotEnd)) {
                if (shot.outputStartSeconds() + TIME_EPSILON < previousShotEnd) {
                    errors.add("montage.timeline.shots_overlap");
                } else if (shot.outputStartSeconds() - previousShotEnd
                        > 1.0 / TICKS_PER_SECOND + TIME_EPSILON) {
                    warnings.add("montage.timeline.shot_output_gap");
                }
            } else if (shot.outputStartSeconds() > TIME_EPSILON) {
                warnings.add("montage.timeline.camera_starts_after_output_zero");
            }
            previousShotEnd = shotEnd;
            lastShotEnd = Math.max(lastShotEnd, shotEnd);
        }

        applyHardCutBoundaries(shotPayloads, forcedFovTicks, errors);
        TreeMap<Integer, MontageTimelineWritePlan.CameraPoint> camera = new TreeMap<>();
        TreeMap<Integer, MontageTimelineWritePlan.FovPoint> rawFov = new TreeMap<>();
        for (ShotPayload shotPayload : shotPayloads) {
            mergeCamera(camera, shotPayload.camera(), errors);
            mergeFov(rawFov, shotPayload.fov(), errors);
        }

        TreeMap<Integer, MontageTimelineWritePlan.TimelapsePoint> timelapse = new TreeMap<>();
        List<MontageTimeMapping> mappings = request.timeMappings().stream()
                .sorted(Comparator.comparingDouble(MontageTimeMapping::outputStartSeconds))
                .toList();
        if (!mappings.equals(request.timeMappings())) errors.add("montage.timeline.mappings_not_ordered");
        MontageTimeMapping previousMapping = null;
        for (MontageTimeMapping mapping : mappings) {
            if (Math.abs(mapping.playbackSpeed() - mapping.derivedPlaybackSpeed()) > SPEED_EPSILON) {
                errors.add("montage.timeline.mapping_speed_mismatch");
            }
            if (previousMapping == null) {
                if (Math.abs(mapping.outputStartSeconds()) > TIME_EPSILON) {
                    errors.add("montage.timeline.mapping_must_start_at_zero");
                }
            } else {
                if (Math.abs(mapping.outputStartSeconds() - previousMapping.outputEndSeconds()) > TIME_EPSILON) {
                    errors.add("montage.timeline.mapping_output_not_contiguous");
                }
                if (mapping.replayStartTime() != previousMapping.replayEndTime()) {
                    errors.add("montage.timeline.source_cut_not_supported");
                }
            }
            addTimelapsePoint(timelapse, mapping.replayStartTime(), mapping.outputStartSeconds(), errors);
            addTimelapsePoint(timelapse, mapping.replayEndTime(), mapping.outputEndSeconds(), errors);
            previousMapping = mapping;
        }

        int previousOutputElapsed = -1;
        for (MontageTimelineWritePlan.TimelapsePoint point : timelapse.values()) {
            if (previousOutputElapsed >= 0 && point.outputElapsedTick() <= previousOutputElapsed) {
                errors.add("montage.timeline.output_not_strictly_increasing");
            }
            previousOutputElapsed = point.outputElapsedTick();
        }
        if (timelapse.size() < 2) errors.add("montage.timeline.timelapse_requires_two_points");

        double outputDuration = mappings.isEmpty() ? 0.0 : mappings.getLast().outputEndSeconds();
        if (lastShotEnd > outputDuration + TIME_EPSILON) {
            errors.add("montage.timeline.camera_exceeds_time_mapping");
        }
        if (camera.size() < 2) errors.add("montage.timeline.camera_requires_two_points");

        TreeMap<Integer, MontageTimelineWritePlan.FovPoint> fov = simplifyFov(rawFov, forcedFovTicks);
        if (fov.size() < 2) errors.add("montage.timeline.fov_requires_two_points");

        MontageTimelineInterval sourceInterval = null;
        if (timelapse.size() >= 2) {
            sourceInterval = new MontageTimelineInterval(timelapse.firstKey(), timelapse.lastKey());
            MontageTimelineInterval finalSourceInterval = sourceInterval;
            boolean outside = camera.keySet().stream().anyMatch(tick -> tick < finalSourceInterval.startTick()
                    || tick > finalSourceInterval.endTick())
                    || fov.keySet().stream().anyMatch(tick -> tick < finalSourceInterval.startTick()
                    || tick > finalSourceInterval.endTick());
            if (outside) errors.add("montage.timeline.keyframe_outside_source_interval");
        }

        int total = camera.size() + fov.size() + timelapse.size();
        if (request.keyframeLimit() > 0 && total > request.keyframeLimit()) {
            errors.add("montage.timeline.keyframe_limit_exceeded");
        }
        if (!errors.isEmpty() || sourceInterval == null) {
            return new BuildResult(Optional.empty(), distinct(errors), distinct(warnings));
        }

        MontageTimelineWritePlan plan = new MontageTimelineWritePlan(request.montageId(),
                (int) request.absoluteOutputStartTick(), sourceInterval, List.copyOf(camera.values()),
                List.copyOf(fov.values()), List.copyOf(timelapse.values()), request.keyframeLimit());
        return new BuildResult(Optional.of(plan), List.of(), distinct(warnings));
    }

    private static void applyHardCutBoundaries(List<ShotPayload> shots, Set<Integer> forcedFovTicks,
                                               List<String> errors) {
        for (int index = 1; index < shots.size(); index++) {
            ShotPayload previous = shots.get(index - 1);
            ShotPayload current = shots.get(index);
            if (previous.camera().isEmpty() || current.camera().isEmpty()) continue;

            int previousLast = previous.camera().lastKey();
            int currentFirst = current.camera().firstKey();
            if (currentFirst < previousLast) {
                errors.add("montage.timeline.shots_source_overlap");
                continue;
            }

            if (currentFirst == previousLast) {
                int holdTick = currentFirst - 1;
                MontageTimelineWritePlan.CameraPoint finalCamera = previous.camera().remove(currentFirst);
                MontageTimelineWritePlan.FovPoint finalFov = previous.fov().remove(currentFirst);
                if (holdTick < 0 || finalCamera == null || finalFov == null) {
                    errors.add("montage.timeline.hard_cut_boundary_too_tight");
                    continue;
                }
                previous.camera().put(holdTick, finalCamera.atTickHoldingAfter(holdTick));
                previous.fov().put(holdTick, finalFov.atTickHoldingAfter(holdTick));
                forcedFovTicks.add(holdTick);
                forcedFovTicks.add(currentFirst);
                if (previous.camera().size() < 2 || previous.fov().size() < 2) {
                    errors.add("montage.timeline.hard_cut_boundary_too_tight");
                }
            } else {
                MontageTimelineWritePlan.CameraPoint finalCamera = previous.camera().get(previousLast);
                MontageTimelineWritePlan.FovPoint finalFov = previous.fov().get(previousLast);
                previous.camera().put(previousLast, finalCamera.atTickHoldingAfter(previousLast));
                previous.fov().put(previousLast, finalFov.atTickHoldingAfter(previousLast));
                forcedFovTicks.add(previousLast);
                forcedFovTicks.add(currentFirst);
            }
        }
    }

    private static void mergeCamera(TreeMap<Integer, MontageTimelineWritePlan.CameraPoint> target,
                                    TreeMap<Integer, MontageTimelineWritePlan.CameraPoint> source,
                                    List<String> errors) {
        source.forEach((tick, point) -> {
            if (target.putIfAbsent(tick, point) != null) {
                errors.add("montage.timeline.camera_source_tick_collision");
            }
        });
    }

    private static void mergeFov(TreeMap<Integer, MontageTimelineWritePlan.FovPoint> target,
                                 TreeMap<Integer, MontageTimelineWritePlan.FovPoint> source,
                                 List<String> errors) {
        source.forEach((tick, point) -> {
            if (target.putIfAbsent(tick, point) != null) {
                errors.add("montage.timeline.fov_source_tick_collision");
            }
        });
    }

    private static void addTimelapsePoint(TreeMap<Integer, MontageTimelineWritePlan.TimelapsePoint> points,
                                          long sourceReplayTick, double outputSeconds, List<String> errors) {
        if (sourceReplayTick < 0 || sourceReplayTick > Integer.MAX_VALUE) {
            errors.add("montage.timeline.source_time_out_of_range");
            return;
        }
        Integer outputElapsedTick = outputElapsedTick(outputSeconds, errors);
        if (outputElapsedTick == null) return;
        int sourceTick = (int) sourceReplayTick;
        MontageTimelineWritePlan.TimelapsePoint existing = points.get(sourceTick);
        if (existing != null && existing.outputElapsedTick() != outputElapsedTick) {
            errors.add("montage.timeline.source_cut_collapsed_to_one_tick");
            return;
        }
        points.put(sourceTick, new MontageTimelineWritePlan.TimelapsePoint(sourceTick, outputElapsedTick));
    }

    private static Integer outputElapsedTick(double outputSeconds, List<String> errors) {
        if (!Double.isFinite(outputSeconds) || outputSeconds < 0.0
                || outputSeconds > Integer.MAX_VALUE / TICKS_PER_SECOND) {
            errors.add("montage.timeline.output_time_out_of_range");
            return null;
        }
        long tick = Math.round(outputSeconds * TICKS_PER_SECOND);
        if (tick < 0 || tick > Integer.MAX_VALUE) {
            errors.add("montage.timeline.output_time_out_of_range");
            return null;
        }
        return (int) tick;
    }

    private static TreeMap<Integer, MontageTimelineWritePlan.FovPoint> simplifyFov(
            TreeMap<Integer, MontageTimelineWritePlan.FovPoint> samples, Set<Integer> forcedTicks) {
        TreeMap<Integer, MontageTimelineWritePlan.FovPoint> result = new TreeMap<>();
        List<MontageTimelineWritePlan.FovPoint> points = List.copyOf(samples.values());
        if (points.isEmpty()) return result;
        result.put(points.getFirst().timelineTick(), points.getFirst());
        for (int index = 1; index < points.size() - 1; index++) {
            MontageTimelineWritePlan.FovPoint previous = points.get(index - 1);
            MontageTimelineWritePlan.FovPoint current = points.get(index);
            MontageTimelineWritePlan.FovPoint next = points.get(index + 1);
            double fraction = (current.timelineTick() - previous.timelineTick())
                    / (double) (next.timelineTick() - previous.timelineTick());
            double expected = previous.fov() + (next.fov() - previous.fov()) * fraction;
            if (current.holdAfter() || forcedTicks.contains(current.timelineTick())
                    || Math.abs(current.fov() - expected) > FOV_SIMPLIFICATION_EPSILON) {
                result.put(current.timelineTick(), current);
            }
        }
        if (points.size() > 1) result.put(points.getLast().timelineTick(), points.getLast());
        return result;
    }

    private static List<String> distinct(List<String> values) {
        return values.stream().distinct().toList();
    }

    private record ShotPayload(TreeMap<Integer, MontageTimelineWritePlan.CameraPoint> camera,
                               TreeMap<Integer, MontageTimelineWritePlan.FovPoint> fov) {
    }

    public record BuildResult(Optional<MontageTimelineWritePlan> plan, List<String> errors, List<String> warnings) {
        public BuildResult {
            plan = plan == null ? Optional.empty() : plan;
            errors = List.copyOf(errors == null ? List.of() : errors);
            warnings = List.copyOf(warnings == null ? List.of() : warnings);
        }

        public boolean valid() {
            return plan.isPresent() && errors.isEmpty();
        }
    }
}
