package pl.peterwolf.cinewolf.montage.preview;

import pl.peterwolf.cinewolf.montage.plan.MontagePlan;

import java.util.Optional;
import java.util.UUID;

public interface MontagePlaybackSession {
    boolean enter(MontagePlan plan);

    void tick();

    void play();

    void pause();

    void stop();

    void seek(double outputSeconds);

    void nextShot();

    void previousShot();

    boolean seekToShot(UUID shotId);

    void exit();

    boolean active();

    MontagePlaybackState state();

    double outputSeconds();

    Optional<UUID> currentShotId();

    String statusKey();
}
