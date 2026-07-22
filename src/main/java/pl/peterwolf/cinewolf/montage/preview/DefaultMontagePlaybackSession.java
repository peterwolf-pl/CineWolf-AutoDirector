package pl.peterwolf.cinewolf.montage.preview;

import pl.peterwolf.cinewolf.montage.plan.MontagePlan;
import pl.peterwolf.cinewolf.montage.plan.PlannedMontageShot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure, non-destructive montage playback state machine.
 * Flashback camera application remains in the client preview controller.
 */
public final class DefaultMontagePlaybackSession implements MontagePlaybackSession {
    private MontagePlan plan;
    private MontagePlaybackState state = MontagePlaybackState.IDLE;
    private double outputSeconds;
    private String statusKey = "cinewolf.montage.preview.idle";

    @Override
    public boolean enter(MontagePlan montagePlan) {
        if (montagePlan == null || montagePlan.enabledShots().isEmpty()) {
            statusKey = "cinewolf.montage.error.preview_unavailable";
            return false;
        }
        plan = montagePlan;
        outputSeconds = 0.0;
        state = MontagePlaybackState.READY;
        statusKey = "cinewolf.montage.preview.paused";
        return true;
    }

    @Override
    public void tick() {
        if (state != MontagePlaybackState.PLAYING || plan == null) return;
        outputSeconds = Math.min(plan.outputDurationSeconds(), outputSeconds + 1.0 / 20.0);
        if (outputSeconds >= plan.outputDurationSeconds() - 1.0e-6) {
            state = MontagePlaybackState.FINISHED;
            statusKey = "cinewolf.montage.preview.finished";
        }
    }

    @Override
    public void play() {
        if (plan == null || state == MontagePlaybackState.RESTORING) return;
        if (outputSeconds >= plan.outputDurationSeconds()) outputSeconds = 0.0;
        state = MontagePlaybackState.PLAYING;
        statusKey = "cinewolf.montage.preview.playing";
    }

    @Override
    public void pause() {
        if (plan == null || state == MontagePlaybackState.RESTORING) return;
        state = MontagePlaybackState.PAUSED;
        statusKey = "cinewolf.montage.preview.paused";
    }

    @Override
    public void stop() {
        if (plan == null) return;
        outputSeconds = 0.0;
        state = MontagePlaybackState.READY;
        statusKey = "cinewolf.montage.preview.stopped";
    }

    @Override
    public void seek(double seconds) {
        if (plan == null) return;
        outputSeconds = Math.max(0.0, Math.min(plan.outputDurationSeconds(), seconds));
        state = MontagePlaybackState.SEEKING;
        statusKey = "cinewolf.montage.preview.paused";
        state = MontagePlaybackState.PAUSED;
    }

    @Override
    public void nextShot() {
        navigate(1);
    }

    @Override
    public void previousShot() {
        navigate(-1);
    }

    @Override
    public boolean seekToShot(UUID shotId) {
        if (plan == null || shotId == null) return false;
        for (PlannedMontageShot shot : plan.enabledShots()) {
            if (shot.shotId().equals(shotId)) {
                seek(shot.outputStartSeconds());
                return true;
            }
        }
        statusKey = "cinewolf.montage.preview.shot_unavailable";
        return false;
    }

    @Override
    public void exit() {
        plan = null;
        outputSeconds = 0.0;
        state = MontagePlaybackState.IDLE;
        statusKey = "cinewolf.montage.preview.exited";
    }

    @Override
    public boolean active() {
        return state != MontagePlaybackState.IDLE;
    }

    @Override
    public MontagePlaybackState state() {
        return state;
    }

    @Override
    public double outputSeconds() {
        return outputSeconds;
    }

    @Override
    public Optional<UUID> currentShotId() {
        if (plan == null) return Optional.empty();
        List<PlannedMontageShot> enabled = plan.enabledShots();
        PlannedMontageShot current = null;
        for (PlannedMontageShot shot : enabled) {
            if (outputSeconds + 1.0e-6 >= shot.outputStartSeconds()) current = shot;
        }
        return Optional.ofNullable(current).map(PlannedMontageShot::shotId);
    }

    @Override
    public String statusKey() {
        return statusKey;
    }

    private void navigate(int delta) {
        if (plan == null) return;
        List<PlannedMontageShot> enabled = plan.enabledShots();
        if (enabled.isEmpty()) return;
        int current = 0;
        for (int index = 0; index < enabled.size(); index++) {
            if (outputSeconds + 1.0e-6 >= enabled.get(index).outputStartSeconds()) current = index;
        }
        int next = Math.max(0, Math.min(enabled.size() - 1, current + delta));
        seek(enabled.get(next).outputStartSeconds());
    }
}
