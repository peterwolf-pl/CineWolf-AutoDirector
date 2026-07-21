package pl.peterwolf.cinewolf.montage.plan;

/**
 * One monotonic mapping between CineWolf output time and source replay time.
 * Output time is expressed in seconds; Flashback source time is expressed in replay ticks.
 */
public record MontageTimeMapping(
        double outputStartSeconds,
        double outputEndSeconds,
        long replayStartTime,
        long replayEndTime,
        double playbackSpeed
) {
    public MontageTimeMapping {
        if (!Double.isFinite(outputStartSeconds) || outputStartSeconds < 0.0
                || !Double.isFinite(outputEndSeconds) || outputEndSeconds <= outputStartSeconds) {
            throw new IllegalArgumentException("Output time mapping must move forwards");
        }
        if (replayStartTime < 0 || replayEndTime <= replayStartTime) {
            throw new IllegalArgumentException("Source replay mapping must move forwards");
        }
        if (!Double.isFinite(playbackSpeed) || playbackSpeed <= 0.0) {
            throw new IllegalArgumentException("Playback speed must be finite and positive");
        }
    }

    public double outputDurationSeconds() {
        return outputEndSeconds - outputStartSeconds;
    }

    public double sourceDurationSeconds() {
        return (replayEndTime - replayStartTime) / 20.0;
    }

    public double derivedPlaybackSpeed() {
        return sourceDurationSeconds() / outputDurationSeconds();
    }

    public static MontageTimeMapping between(double outputStartSeconds, double outputEndSeconds,
                                             long replayStartTime, long replayEndTime) {
        double outputDuration = outputEndSeconds - outputStartSeconds;
        if (!Double.isFinite(outputDuration) || outputDuration <= 0.0) {
            throw new IllegalArgumentException("Output time mapping must move forwards");
        }
        double speed = ((replayEndTime - replayStartTime) / 20.0) / outputDuration;
        return new MontageTimeMapping(outputStartSeconds, outputEndSeconds, replayStartTime, replayEndTime, speed);
    }
}
