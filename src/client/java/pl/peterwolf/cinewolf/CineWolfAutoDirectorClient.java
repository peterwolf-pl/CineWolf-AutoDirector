package pl.peterwolf.cinewolf;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.peterwolf.cinewolf.camera.CameraPathPlanner;
import pl.peterwolf.cinewolf.config.CineWolfConfigManager;
import pl.peterwolf.cinewolf.integration.flashback.FlashbackCompatibility;
import pl.peterwolf.cinewolf.integration.flashback.FlashbackReplayEditorAdapter;
import pl.peterwolf.cinewolf.preview.CameraPathPreviewRenderer;
import pl.peterwolf.cinewolf.preview.PreviewController;
import pl.peterwolf.cinewolf.preview.VerticalSafeAreaOverlay;
import pl.peterwolf.cinewolf.montage.MontageAnalysisController;
import pl.peterwolf.cinewolf.montage.MontageGenerationController;
import pl.peterwolf.cinewolf.montage.MontagePreviewController;
import pl.peterwolf.cinewolf.ui.AutoDirectorPanel;
import pl.peterwolf.cinewolf.ui.GenerateMontagePanel;

public final class CineWolfAutoDirectorClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(CineWolfAutoDirector.MOD_NAME);
    private static AutoDirectorPanel panel;
    private static PreviewController previewController;
    private static CameraPathPreviewRenderer previewRenderer;
    private static MontageAnalysisController montageAnalysisController;
    private static MontageGenerationController montageGenerationController;
    private static MontagePreviewController montagePreviewController;
    private static VerticalSafeAreaOverlay verticalSafeAreaOverlay;
    private static boolean compatibilityMessageShown;

    @Override
    public void onInitializeClient() {
        CineWolfConfigManager configManager = new CineWolfConfigManager(LOGGER);
        configManager.load();
        if (!FlashbackCompatibility.isSupportedRuntime()) {
            LOGGER.error("{}. Supported range: exactly {}.", FlashbackCompatibility.failureMessage(),
                    FlashbackCompatibility.SUPPORTED_VERSION);
            ClientTickEvents.END_CLIENT_TICK.register(client -> showCompatibilityMessage(client,
                    FlashbackCompatibility.failureMessage()));
            return;
        }

        FlashbackReplayEditorAdapter adapter = new FlashbackReplayEditorAdapter(LOGGER);
        previewRenderer = new CameraPathPreviewRenderer(adapter);
        previewRenderer.register();
        previewRenderer.setVisible(configManager.get().previewVisible
                || configManager.get().montage.debugVisualization);
        previewController = new PreviewController(adapter, CameraPathPlanner.createDefault(), previewRenderer, LOGGER);
        montageAnalysisController = new MontageAnalysisController(adapter, configManager.get(), LOGGER);
        montageGenerationController = new MontageGenerationController(adapter, configManager.get(), previewRenderer, LOGGER);
        montagePreviewController = new MontagePreviewController(adapter);
        verticalSafeAreaOverlay = new VerticalSafeAreaOverlay();
        GenerateMontagePanel montagePanel = new GenerateMontagePanel(adapter, configManager,
                montageAnalysisController, montageGenerationController, montagePreviewController,
                verticalSafeAreaOverlay, previewController, LOGGER);
        panel = new AutoDirectorPanel(adapter, previewController, configManager, montagePanel);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!adapter.isReplayEditorOpen() && verticalSafeAreaOverlay != null) {
                verticalSafeAreaOverlay.hide();
            }
            previewController.tick();
            montageAnalysisController.tick();
            montageGenerationController.tick();
            montagePreviewController.tick();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            previewController.close();
            montageAnalysisController.close();
            montageGenerationController.close();
            montagePreviewController.exit();
            if (verticalSafeAreaOverlay != null) verticalSafeAreaOverlay.hide();
        });
        LOGGER.info("CineWolf AutoDirector {} initialized for Minecraft 26.2 and Flashback {}",
                CineWolfAutoDirector.VERSION, FlashbackCompatibility.SUPPORTED_VERSION);
    }

    public static void renderPanel() {
        if (panel != null) panel.render();
        if (verticalSafeAreaOverlay != null) verticalSafeAreaOverlay.render();
    }

    public static void setPreviewVisible(boolean visible) {
        if (previewRenderer != null) previewRenderer.setVisible(visible);
    }

    private static void showCompatibilityMessage(Minecraft client, String message) {
        if (compatibilityMessageShown || client.player == null) return;
        compatibilityMessageShown = true;
        client.gui.chatListener().handleSystemMessage(
                Component.literal("[CineWolf] " + message).withStyle(ChatFormatting.RED), false);
    }
}
