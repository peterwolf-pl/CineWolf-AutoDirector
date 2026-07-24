package pl.peterwolf.cinewolf.integration.flashback;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.combo_options.AspectRatio;
import com.moulberry.flashback.combo_options.Sizing;
import com.moulberry.flashback.configuration.FlashbackConfigV1;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.visuals.ReplayVisuals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.peterwolf.cinewolf.montage.preset.OutputAspectRatio;
import pl.peterwolf.cinewolf.montage.preset.VerticalComposition;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Applies CineWolf output aspect ratio to Flashback preview sizing and export resolution.
 * Flashback StartExportWindow already maps ASPECT_9_16 → 1080×1920 when sizing is CHANGE_ASPECT_RATIO;
 * we set both visuals and internalExport.resolution so export opens already in 9:16.
 */
public final class FlashbackExportAspectSync {
    private static final Logger LOGGER = LoggerFactory.getLogger(FlashbackExportAspectSync.class);
    private static final AtomicBoolean LOGGED_FAILURE = new AtomicBoolean(false);

    private FlashbackExportAspectSync() {
    }

    public static boolean apply(OutputAspectRatio aspect) {
        Objects.requireNonNull(aspect, "aspect");
        if (!FlashbackCompatibility.isSupportedRuntime()) return false;
        try {
            EditorState state = EditorStateManager.getCurrent();
            if (state == null) return false;
            ReplayVisuals visuals = state.replayVisuals;
            if (visuals == null) return false;

            AspectRatio flashbackAspect = aspect.vertical()
                    ? AspectRatio.ASPECT_9_16
                    : AspectRatio.ASPECT_16_9;
            visuals.sizing = Sizing.CHANGE_ASPECT_RATIO;
            visuals.changeAspectRatio = flashbackAspect;

            int[] resolution = VerticalComposition.exportResolution(aspect);
            FlashbackConfigV1 config = Flashback.getConfig();
            if (config != null && config.internalExport != null) {
                if (config.internalExport.resolution == null || config.internalExport.resolution.length < 2
                        || config.internalExport.resolution[0] != resolution[0]
                        || config.internalExport.resolution[1] != resolution[1]) {
                    config.internalExport.resolution = Arrays.copyOf(resolution, 2);
                }
                if (config.forceDefaultExportSettings != null) {
                    config.forceDefaultExportSettings.resolution = Arrays.copyOf(resolution, 2);
                }
                config.delayedSaveToDefaultFolder();
            }
            state.markDirty();
            return true;
        } catch (RuntimeException exception) {
            if (LOGGED_FAILURE.compareAndSet(false, true)) {
                LOGGER.warn("Unable to apply Flashback export aspect {}: {}", aspect, exception.toString());
            }
            return false;
        }
    }

    public static int[] currentExportResolution() {
        try {
            FlashbackConfigV1 config = Flashback.getConfig();
            if (config != null && config.internalExport != null
                    && config.internalExport.resolution != null
                    && config.internalExport.resolution.length >= 2) {
                return Arrays.copyOf(config.internalExport.resolution, 2);
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        return VerticalComposition.exportResolution(OutputAspectRatio.LANDSCAPE_16_9);
    }
}
