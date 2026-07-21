package pl.peterwolf.cinewolf.montage.timeline;

import pl.peterwolf.cinewolf.model.EasingType;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Fully validated, Flashback-neutral keyframe payload.
 *
 * <p>Flashback evaluates every native track on the source replay-tick axis. The historical
 * {@code outputInterval} name is retained for UI/API compatibility, but the interval and every
 * point's {@code timelineTick} are source replay ticks. Timelapse values contain elapsed output
 * ticks.</p>
 */
public record MontageTimelineWritePlan(
        UUID montageId,
        int requestedOutputStartTick,
        MontageTimelineInterval outputInterval,
        List<CameraPoint> cameraKeyframes,
        List<FovPoint> fovKeyframes,
        List<TimelapsePoint> timelapseKeyframes,
        int keyframeLimit
) {
    public MontageTimelineWritePlan {
        Objects.requireNonNull(montageId, "montageId");
        Objects.requireNonNull(outputInterval, "outputInterval");
        cameraKeyframes = List.copyOf(cameraKeyframes);
        fovKeyframes = List.copyOf(fovKeyframes);
        timelapseKeyframes = List.copyOf(timelapseKeyframes);
        if (requestedOutputStartTick < 0 || keyframeLimit <= 0) {
            throw new IllegalArgumentException("Validated montage plan contains invalid limits");
        }
    }

    /** Native source-replay interval occupied by this payload. */
    public MontageTimelineInterval sourceInterval() {
        return outputInterval;
    }

    /** @deprecated Native placement is source-bound; use {@link #sourceInterval()}. */
    @Deprecated
    public int effectiveOutputStartTick() {
        return outputInterval.startTick();
    }

    public int totalKeyframes() {
        return Math.addExact(Math.addExact(cameraKeyframes.size(), fovKeyframes.size()), timelapseKeyframes.size());
    }

    public record CameraPoint(int timelineTick, Vec3d position, double yaw, double pitch, double roll,
                              EasingType easing, boolean holdAfter) {
        public CameraPoint {
            if (timelineTick < 0) throw new IllegalArgumentException("Camera timeline tick cannot be negative");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(easing, "easing");
        }

        public CameraPoint(int timelineTick, Vec3d position, double yaw, double pitch, double roll,
                           EasingType easing) {
            this(timelineTick, position, yaw, pitch, roll, easing, false);
        }

        CameraPoint atTickHoldingAfter(int newTimelineTick) {
            return new CameraPoint(newTimelineTick, position, yaw, pitch, roll, easing, true);
        }
    }

    public record FovPoint(int timelineTick, double fov, EasingType easing, boolean holdAfter) {
        public FovPoint {
            if (timelineTick < 0) throw new IllegalArgumentException("FOV timeline tick cannot be negative");
            Objects.requireNonNull(easing, "easing");
        }

        public FovPoint(int timelineTick, double fov, EasingType easing) {
            this(timelineTick, fov, easing, false);
        }

        FovPoint atTickHoldingAfter(int newTimelineTick) {
            return new FovPoint(newTimelineTick, fov, easing, true);
        }
    }

    /**
     * A Flashback timelapse point: source replay tick on the native x-axis and elapsed output
     * tick as the TimelapseKeyframe value.
     */
    public record TimelapsePoint(int timelineTick, int outputElapsedTick) {
        public TimelapsePoint {
            if (timelineTick < 0 || outputElapsedTick < 0) {
                throw new IllegalArgumentException("Timelapse ticks cannot be negative");
            }
        }

        /** Flashback playback rate between this point and the strictly later point. */
        public double ticksPerSecondTo(TimelapsePoint next) {
            Objects.requireNonNull(next, "next");
            int sourceDelta = Math.subtractExact(next.timelineTick, timelineTick);
            int outputDelta = Math.subtractExact(next.outputElapsedTick, outputElapsedTick);
            if (sourceDelta <= 0 || outputDelta <= 0) {
                throw new IllegalArgumentException("Timelapse points must increase on both axes");
            }
            return sourceDelta / (double) outputDelta * 20.0;
        }
    }
}
