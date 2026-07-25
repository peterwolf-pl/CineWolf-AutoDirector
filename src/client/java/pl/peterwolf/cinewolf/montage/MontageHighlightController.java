package pl.peterwolf.cinewolf.montage;

import com.moulberry.flashback.combo_options.MarkerColour;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import pl.peterwolf.cinewolf.input.CineWolfKeybinds;
import pl.peterwolf.cinewolf.integration.flashback.FlashbackReplayEditorAdapter;
import pl.peterwolf.cinewolf.montage.highlight.MontageHighlight;
import pl.peterwolf.cinewolf.montage.highlight.MontageHighlightStore;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Handles hotkeys for marking montage moments/fragments during replay or recording. */
public final class MontageHighlightController {
    private static final long MOMENT_PADDING_TICKS = 30L; // ±1.5 s

    private final FlashbackReplayEditorAdapter adapter;
    private final MontageHighlightStore store;
    private final Logger logger;

    public MontageHighlightController(FlashbackReplayEditorAdapter adapter, MontageHighlightStore store,
                                      Logger logger) {
        this.adapter = Objects.requireNonNull(adapter);
        this.store = Objects.requireNonNull(store);
        this.logger = Objects.requireNonNull(logger);
    }

    public MontageHighlightStore store() {
        return store;
    }

    public void tick() {
        bindActiveTimeline();

        if (!adapter.isInReplay() && !adapter.isRecording()) return;

        while (CineWolfKeybinds.MARK_MOMENT.consumeClick()) markMoment();
        while (CineWolfKeybinds.MARK_FRAGMENT.consumeClick()) toggleFragment();
        while (CineWolfKeybinds.CANCEL_FRAGMENT.consumeClick()) cancelFragment();
    }

    private void bindActiveTimeline() {
        if (adapter.isInReplay()) {
            store.setActiveReplay(adapter.replayIdentifier());
            return;
        }
        if (adapter.isRecording()) {
            // Use Flashback's recording UUID so highlights reappear when the finished replay is opened.
            UUID recordingId = adapter.recordingReplayIdentifier();
            store.setActiveReplay(recordingId != null
                    ? recordingId
                    : UUID.nameUUIDFromBytes("recording-session".getBytes()));
        }
    }

    private void markMoment() {
        long tick = currentTick();
        if (tick < 0) {
            toast("cinewolf.highlight.error.no_time", ChatFormatting.RED);
            return;
        }
        try {
            MontageHighlight highlight = store.addMoment(tick, "moment@" + tick, MOMENT_PADDING_TICKS);
            boolean nativeWritten = adapter.writeNativeMarker(tick, "CineWolf: " + highlight.label(),
                    MarkerColour.ORANGE);
            toast(nativeWritten ? "cinewolf.highlight.moment_saved_native" : "cinewolf.highlight.moment_saved",
                    ChatFormatting.GREEN, formatSeconds(tick), highlight.durationSeconds());
            logger.info("CineWolf montage moment marked at tick {} (nativeMarker={})", tick, nativeWritten);
        } catch (RuntimeException exception) {
            logger.warn("Unable to mark montage moment", exception);
            toast("cinewolf.highlight.error.failed", ChatFormatting.RED, exception.getMessage());
        }
    }

    private void toggleFragment() {
        long tick = currentTick();
        if (tick < 0) {
            toast("cinewolf.highlight.error.no_time", ChatFormatting.RED);
            return;
        }
        try {
            Optional<MontageHighlight> completed = store.toggleFragment(tick, "fragment");
            if (completed.isEmpty()) {
                toast("cinewolf.highlight.fragment_started", ChatFormatting.YELLOW, formatSeconds(tick));
                adapter.writeNativeMarker(tick, "CineWolf fragment start", MarkerColour.YELLOW);
                return;
            }
            MontageHighlight highlight = completed.get();
            adapter.writeNativeMarker(highlight.endTick(), "CineWolf fragment end", MarkerColour.LIME);
            toast("cinewolf.highlight.fragment_saved", ChatFormatting.GREEN,
                    formatSeconds(highlight.startTick()), formatSeconds(highlight.endTick()),
                    format(highlight.durationSeconds()));
            logger.info("CineWolf montage fragment marked {}..{} ({} ticks)",
                    highlight.startTick(), highlight.endTick(), highlight.durationTicks());
        } catch (RuntimeException exception) {
            logger.warn("Unable to mark montage fragment", exception);
            toast("cinewolf.highlight.error.failed", ChatFormatting.RED, exception.getMessage());
        }
    }

    private void cancelFragment() {
        if (store.cancelPendingFragment()) {
            toast("cinewolf.highlight.fragment_cancelled", ChatFormatting.GRAY);
        }
    }

    private long currentTick() {
        return adapter.getCurrentTimelineTick();
    }

    private static String formatSeconds(long tick) {
        double seconds = tick / 20.0;
        int total = (int) Math.floor(seconds);
        int m = total / 60;
        int s = total % 60;
        return String.format("%d:%02d", m, s);
    }

    private static String format(double seconds) {
        return String.format(java.util.Locale.ROOT, "%.1f", seconds);
    }

    private static void toast(String key, ChatFormatting color, Object... args) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        Component message = Component.translatable(key, args).withStyle(color);
        client.gui.chatListener().handleSystemMessage(
                Component.literal("[CineWolf] ").withStyle(ChatFormatting.AQUA).append(message), false);
    }
}
