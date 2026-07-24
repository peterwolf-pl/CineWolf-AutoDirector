package pl.peterwolf.cinewolf;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.loader.api.FabricLoader;
import pl.peterwolf.cinewolf.camera.CameraPathPlanner;
import pl.peterwolf.cinewolf.clip.OcclusionClipController;
import pl.peterwolf.cinewolf.config.CineWolfConfigManager;
import pl.peterwolf.cinewolf.input.CineWolfKeybinds;
import pl.peterwolf.cinewolf.integration.flashback.FlashbackCompatibility;
import pl.peterwolf.cinewolf.integration.flashback.FlashbackReplayEditorAdapter;
import pl.peterwolf.cinewolf.montage.MontageHighlightController;
import pl.peterwolf.cinewolf.montage.highlight.MontageHighlightStore;
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
    private static MontageHighlightController montageHighlightController;
    private static VerticalSafeAreaOverlay verticalSafeAreaOverlay;
    private static boolean compatibilityMessageShown;
    private static boolean editorIntegrationEnabled;

    @Override
    public void onInitializeClient() {
        CineWolfConfigManager configManager = new CineWolfConfigManager(LOGGER);
        configManager.load();

        // Integration registration and config remain available without Flashback.
        CineWolfAutoDirectorMod.integrations();

        if (!FlashbackCompatibility.isSupportedRuntime()) {
            FlashbackCompatibility.logFailureOnce(LOGGER);
            editorIntegrationEnabled = false;
            ClientTickEvents.END_CLIENT_TICK.register(client -> showCompatibilityMessage(client,
                    FlashbackCompatibility.failureMessage()));
            LOGGER.info("CineWolf AutoDirector {} loaded without Flashback editor integration",
                    CineWolfAutoDirector.VERSION);
            return;
        }

        editorIntegrationEnabled = true;
        CineWolfKeybinds.register();
        FlashbackReplayEditorAdapter adapter = new FlashbackReplayEditorAdapter(LOGGER);
        OcclusionClipController.get().bindConfig(configManager.get());
        previewRenderer = new CameraPathPreviewRenderer(adapter);
        previewRenderer.register();
        previewRenderer.setVisible(configManager.get().previewVisible
                || configManager.get().montage.debugVisualization);
        previewController = new PreviewController(adapter, CameraPathPlanner.createDefault(), previewRenderer, LOGGER);
        MontageHighlightStore highlightStore = new MontageHighlightStore(
                FabricLoader.getInstance().getConfigDir().resolve("cinewolf-autodirector-highlights.json"), LOGGER);
        montageHighlightController = new MontageHighlightController(adapter, highlightStore, LOGGER);
        montageAnalysisController = new MontageAnalysisController(adapter, configManager.get(), LOGGER, highlightStore);
        montageGenerationController = new MontageGenerationController(adapter, configManager.get(), previewRenderer, LOGGER);
        montagePreviewController = new MontagePreviewController(adapter);
        verticalSafeAreaOverlay = new VerticalSafeAreaOverlay();
        GenerateMontagePanel montagePanel = new GenerateMontagePanel(adapter, configManager,
                montageAnalysisController, montageGenerationController, montagePreviewController,
                verticalSafeAreaOverlay, previewController, LOGGER, highlightStore);
        panel = new AutoDirectorPanel(adapter, previewController, configManager, montagePanel);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!adapter.isReplayEditorOpen() && verticalSafeAreaOverlay != null) {
                verticalSafeAreaOverlay.hide();
            }
            if (adapter.isReplayEditorOpen()) {
                OcclusionClipController.get().tick();
            } else {
                OcclusionClipController.get().clear();
            }
            montageHighlightController.tick();
            previewController.tick();
            montageAnalysisController.tick();
            montageGenerationController.tick();
            montagePreviewController.tick();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            OcclusionClipController.get().clear();
            previewController.close();
            montageAnalysisController.close();
            montageGenerationController.close();
            montagePreviewController.exit();
            adapter.close();
            if (verticalSafeAreaOverlay != null) verticalSafeAreaOverlay.hide();
        });
        LOGGER.info("CineWolf AutoDirector {} initialized for Minecraft 26.2 and Flashback {}",
                CineWolfAutoDirector.VERSION, FlashbackCompatibility.SUPPORTED_VERSION);
    }

    public static boolean isEditorIntegrationEnabled() {
        return editorIntegrationEnabled;
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
