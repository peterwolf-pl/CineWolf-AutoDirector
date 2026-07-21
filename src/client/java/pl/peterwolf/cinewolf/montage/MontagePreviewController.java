package pl.peterwolf.cinewolf.montage;

import pl.peterwolf.cinewolf.api.ReplayEditorAdapter;
import pl.peterwolf.cinewolf.camera.CameraMath;
import pl.peterwolf.cinewolf.integration.flashback.FlashbackReplayEditorAdapter;
import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.montage.plan.MontagePlan;
import pl.peterwolf.cinewolf.montage.plan.MontageTimeMapping;
import pl.peterwolf.cinewolf.montage.plan.PlannedMontageShot;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Non-destructive in-memory camera preview. No temporary native keyframes are written. */
public final class MontagePreviewController {
    private static final int REQUIRED_STABLE_TICKS = 1;
    private final FlashbackReplayEditorAdapter adapter;
    private boolean active;
    private boolean playing;
    private boolean restoring;
    private MontagePlan plan;
    private List<MontageGenerationController.GeneratedPath> paths = List.of();
    private double outputSeconds;
    private long restoreTick;
    private boolean restorePaused;
    private ReplayEditorAdapter.CameraPose restoreCamera;
    private long requestedSourceTick = Long.MIN_VALUE;
    private int stableTicks;
    private String statusKey = "cinewolf.montage.preview.idle";

    public MontagePreviewController(FlashbackReplayEditorAdapter adapter) {
        this.adapter = adapter;
    }

    public boolean enter(MontagePlan montagePlan, List<MontageGenerationController.GeneratedPath> generated) {
        if (!adapter.isReplayEditorOpen() || montagePlan == null || generated == null || generated.isEmpty()) {
            statusKey = "cinewolf.montage.error.preview_unavailable";
            return false;
        }
        if (active || restoring) {
            statusKey = "cinewolf.montage.preview.already_active";
            return false;
        }
        plan = montagePlan;
        paths = List.copyOf(generated);
        restoreTick = adapter.getCurrentReplayTime();
        restorePaused = adapter.replayPaused();
        restoreCamera = adapter.getCurrentCameraPose();
        adapter.setReplayPaused(true);
        outputSeconds = 0.0;
        requestedSourceTick = Long.MIN_VALUE;
        stableTicks = 0;
        active = true;
        playing = false;
        restoring = false;
        statusKey = "cinewolf.montage.preview.paused";
        seekForCurrentFrame();
        return true;
    }

    public void tick() {
        if (!active) return;
        if (!adapter.isReplayEditorOpen()) {
            clearWithoutRestore();
            return;
        }
        if (restoring) {
            tickRestore();
            return;
        }
        long source = sourceTick(outputSeconds);
        if (source < 0) {
            playing = false;
            statusKey = "cinewolf.montage.preview.no_mapping";
            return;
        }
        if (requestedSourceTick != source || adapter.getCurrentReplayTime() != source
                || !adapter.replayStateReady(source)) {
            requestedSourceTick = source;
            stableTicks = 0;
            if (adapter.getCurrentReplayTime() != source) adapter.goToReplayTick(source);
            return;
        }
        stableTicks++;
        if (stableTicks < REQUIRED_STABLE_TICKS) return;
        stableTicks = 0;
        ReplayEditorAdapter.CameraPose pose = cameraPose(outputSeconds);
        if (pose != null) adapter.applyPreviewCameraPose(pose);
        if (!playing) return;
        outputSeconds = Math.min(plan.outputDurationSeconds(), outputSeconds + 1.0 / 20.0);
        if (outputSeconds >= plan.outputDurationSeconds() - 1.0e-6) {
            playing = false;
            statusKey = "cinewolf.montage.preview.finished";
        } else {
            requestedSourceTick = Long.MIN_VALUE;
            statusKey = "cinewolf.montage.preview.playing";
        }
    }

    public void play() {
        if (!active || restoring) return;
        if (outputSeconds >= plan.outputDurationSeconds()) outputSeconds = 0.0;
        playing = true;
        statusKey = "cinewolf.montage.preview.playing";
    }

    public void pause() {
        if (!active || restoring) return;
        playing = false;
        statusKey = "cinewolf.montage.preview.paused";
    }

    public void stop() {
        if (!active || restoring) return;
        playing = false;
        outputSeconds = 0.0;
        requestedSourceTick = Long.MIN_VALUE;
        statusKey = "cinewolf.montage.preview.stopped";
        seekForCurrentFrame();
    }

    public void previousShot() {
        navigateShot(-1);
    }

    public void nextShot() {
        navigateShot(1);
    }

    public void seekToShot(int index) {
        if (!active || restoring || plan == null) return;
        List<PlannedMontageShot> enabled = plan.enabledShots();
        if (enabled.isEmpty()) return;
        PlannedMontageShot shot = enabled.get(Math.max(0, Math.min(enabled.size() - 1, index)));
        playing = false;
        outputSeconds = shot.outputStartSeconds();
        requestedSourceTick = Long.MIN_VALUE;
        statusKey = "cinewolf.montage.preview.paused";
        seekForCurrentFrame();
    }

    public boolean seekToShot(UUID shotId) {
        if (!active || restoring || plan == null || shotId == null) return false;
        List<PlannedMontageShot> enabled = plan.enabledShots();
        for (int index = 0; index < enabled.size(); index++) {
            if (enabled.get(index).shotId().equals(shotId)) {
                seekToShot(index);
                return true;
            }
        }
        statusKey = "cinewolf.montage.preview.shot_unavailable";
        return false;
    }

    private void navigateShot(int delta) {
        if (!active || restoring || plan == null) return;
        List<PlannedMontageShot> enabled = plan.enabledShots();
        if (enabled.isEmpty()) return;
        int current = 0;
        for (int index = 0; index < enabled.size(); index++) {
            if (outputSeconds + 1.0e-6 >= enabled.get(index).outputStartSeconds()) current = index;
        }
        seekToShot(Math.max(0, Math.min(enabled.size() - 1, current + delta)));
    }

    public void exit() {
        if (!active) return;
        playing = false;
        restoring = true;
        stableTicks = 0;
        statusKey = "cinewolf.montage.preview.restoring";
        adapter.goToReplayTick(restoreTick);
    }

    private void tickRestore() {
        if (adapter.getCurrentReplayTime() != restoreTick || !adapter.replayStateReady(restoreTick)) {
            stableTicks = 0;
            if (adapter.getCurrentReplayTime() != restoreTick) adapter.goToReplayTick(restoreTick);
            return;
        }
        stableTicks++;
        if (stableTicks < 2) return;
        adapter.applyPreviewCameraPose(restoreCamera);
        adapter.setReplayPaused(restorePaused);
        clearWithoutRestore();
        statusKey = "cinewolf.montage.preview.exited";
    }

    private void seekForCurrentFrame() {
        long source = sourceTick(outputSeconds);
        if (source >= 0) {
            requestedSourceTick = source;
            stableTicks = 0;
            adapter.goToReplayTick(source);
        }
    }

    private long sourceTick(double outputTime) {
        if (plan == null || plan.timeMappings().isEmpty()) return -1;
        MontageTimeMapping mapping = plan.timeMappings().stream()
                .filter(value -> outputTime + 1.0e-7 >= value.outputStartSeconds()
                        && outputTime <= value.outputEndSeconds() + 1.0e-7)
                .findFirst().orElse(plan.timeMappings().getLast());
        double fraction = Math.max(0.0, Math.min(1.0,
                (outputTime - mapping.outputStartSeconds()) / mapping.outputDurationSeconds()));
        return Math.round(mapping.replayStartTime()
                + (mapping.replayEndTime() - mapping.replayStartTime()) * fraction);
    }

    private ReplayEditorAdapter.CameraPose cameraPose(double outputTime) {
        MontageGenerationController.GeneratedPath generated = paths.stream()
                .filter(value -> outputTime + 1.0e-7 >= value.shot().outputStartSeconds()
                        && outputTime <= value.shot().outputEndSeconds() + 1.0e-7)
                .findFirst().orElseGet(() -> paths.stream()
                        .min(Comparator.comparingDouble(value -> Math.abs(value.shot().outputStartSeconds() - outputTime)))
                        .orElse(null));
        if (generated == null) return null;
        double local = Math.max(0.0, Math.min(generated.shot().outputDurationSeconds(),
                outputTime - generated.shot().outputStartSeconds()));
        List<CameraSample> samples = generated.path().samples();
        if (samples.isEmpty()) return null;
        CameraSample left = samples.getFirst();
        CameraSample right = samples.getLast();
        for (int index = 1; index < samples.size(); index++) {
            if (samples.get(index).cinematicTimeSeconds() >= local) {
                left = samples.get(index - 1);
                right = samples.get(index);
                break;
            }
        }
        double interval = right.cinematicTimeSeconds() - left.cinematicTimeSeconds();
        double fraction = interval <= 1.0e-9 ? 0.0
                : Math.max(0.0, Math.min(1.0, (local - left.cinematicTimeSeconds()) / interval));
        Vec3d position = left.position().lerp(right.position(), fraction);
        double unwrappedYaw = CameraMath.unwrapDegrees(left.yaw(), right.yaw());
        double yaw = left.yaw() + (unwrappedYaw - left.yaw()) * fraction;
        double pitch = left.pitch() + (right.pitch() - left.pitch()) * fraction;
        double roll = left.roll() + (right.roll() - left.roll()) * fraction;
        double fov = left.fov() + (right.fov() - left.fov()) * fraction;
        return new ReplayEditorAdapter.CameraPose(position, yaw, pitch, roll, fov);
    }

    private void clearWithoutRestore() {
        active = false;
        playing = false;
        restoring = false;
        plan = null;
        paths = List.of();
        requestedSourceTick = Long.MIN_VALUE;
        stableTicks = 0;
    }

    public boolean active() {
        return active;
    }

    public boolean playing() {
        return playing;
    }

    public boolean restoring() {
        return restoring;
    }

    public double outputSeconds() {
        return outputSeconds;
    }

    public String statusKey() {
        return statusKey;
    }
}
