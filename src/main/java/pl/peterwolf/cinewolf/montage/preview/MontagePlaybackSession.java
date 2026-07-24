package pl.peterwolf.cinewolf.montage.preview;

import pl.peterwolf.cinewolf.montage.plan.MontagePlan;

import java.util.Optional;
import java.util.UUID;

public interface MontagePlaybackSession extends AutoCloseable {
    boolean enter(MontagePlan plan);

    void tick();

    void play();

    void pause();

    void stop();

    void seek(double outputSeconds);

    default void seekOutputTime(double outputTime) {
        seek(outputTime);
    }

    void nextShot();

    void previousShot();

    boolean seekToShot(UUID shotId);

    default void setLoopMode(LoopMode loopMode) {
        // optional
    }

    default LoopMode loopMode() {
        return LoopMode.NONE;
    }

    default void setPlaybackSpeed(double speed) {
        // optional
    }

    default double playbackSpeed() {
        return 1.0;
    }

    void exit();

    boolean active();

    MontagePlaybackState state();

    double outputSeconds();

    Optional<UUID> currentShotId();

    String statusKey();

    @Override
    default void close() {
        exit();
    }

    enum LoopMode {
        NONE,
        MONTAGE,
        SHOT
    }
}
