package pl.peterwolf.cinewolf.preview;

import com.moulberry.flashback.editor.ui.ReplayUI;
import imgui.moulberry90.ImDrawList;
import imgui.moulberry90.ImGui;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import pl.peterwolf.cinewolf.CineWolfAutoDirector;
import pl.peterwolf.cinewolf.config.CineWolfConfig;

/**
 * TV-style brand bug: slightly transparent CineWolf icon in the top-right corner of the
 * game/export frame for every AutoDirector montage video.
 * <p>
 * Drawn both via the Fabric HUD (baked into Flashback export frames that include the GUI)
 * and as a lightweight ImGui overlay on the Flashback replay viewport for editor preview.
 */
public final class CineWolfBrandWatermark implements HudElement {
    public static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(CineWolfAutoDirector.MOD_ID, "textures/gui/watermark.png");
    private static final Identifier HUD_ID =
            Identifier.fromNamespaceAndPath(CineWolfAutoDirector.MOD_ID, "brand_watermark");

    /** ~55% opacity — readable but not distracting. */
    private static final float DEFAULT_ALPHA = 0.55f;
    /** Base size as a fraction of frame width at scale 1.0. */
    private static final float MIN_SIZE_FRAC = 1f / 18f;
    private static final int MIN_SIZE_PX = 16;
    private static final int MAX_SIZE_PX = 480;
    private static final float DEFAULT_SCALE = 1.0f;

    private final CineWolfConfig config;
    private volatile boolean active;

    public CineWolfBrandWatermark(CineWolfConfig config) {
        this.config = config;
    }

    public static CineWolfBrandWatermark register(CineWolfConfig config) {
        CineWolfBrandWatermark watermark = new CineWolfBrandWatermark(config);
        // After misc overlays so the bug sits on top of world/HUD chrome.
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, HUD_ID, watermark);
        return watermark;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean active() {
        return active;
    }

    public boolean enabledInConfig() {
        return config != null && config.montage != null && config.montage.exportWatermark;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
        if (!shouldDrawHud()) return;
        Minecraft client = Minecraft.getInstance();
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        int size = resolveSizePx(screenW, screenH);
        int margin = Math.max(6, Math.round(screenW * 0.012f));
        int x = screenW - size - margin;
        int y = margin;
        // Keep inside vertical frame too (vertical 9:16 exports).
        if (y + size > screenH - margin) {
            size = Math.max(MIN_SIZE_PX, screenH - 2 * margin);
            x = screenW - size - margin;
        }
        float alpha = clampAlpha(config.montage.exportWatermarkOpacity);
        int color = ARGB.white(alpha);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0f, 0.0f,
                size, size, size, size, color);
    }

    /**
     * Editor viewport preview (ImGui). Flashback export uses the game HUD path above;
     * this keeps the bug visible while reviewing in the replay editor.
     */
    public void renderEditorOverlay() {
        if (!enabledInConfig() || !active) return;
        if (ReplayUI.frameWidth <= 0 || ReplayUI.frameHeight <= 0) return;
        float frameX = ReplayUI.frameX;
        float frameY = ReplayUI.frameY;
        float frameW = ReplayUI.frameWidth;
        float frameH = ReplayUI.frameHeight;
        float size = resolveSize(frameW, frameH);
        float margin = Math.max(6f, frameW * 0.012f);
        float x0 = frameX + frameW - size - margin;
        float y0 = frameY + margin;
        float x1 = x0 + size;
        float y1 = y0 + size;
        float alpha = clampAlpha(config.montage.exportWatermarkOpacity);
        int a = Math.round(alpha * 255f) & 0xFF;
        // ImGui packed color: ABGR little-endian (AARRGGBB as bytes A,B,G,R in memory → 0xAABBGGRR).
        int fill = (a << 24) | 0x001A1A1A;
        int border = (Math.min(255, a + 50) << 24) | 0x00E0E0E0;
        int textCol = (a << 24) | 0x00FFFFFF;
        ImDrawList draw = ImGui.getForegroundDrawList();
        draw.addRectFilled(x0, y0, x1, y1, fill, size * 0.18f);
        draw.addRect(x0, y0, x1, y1, border, size * 0.18f, 0, 1.5f);
        // Editor-only monogram; export path uses the real PNG via HUD.
        String mark = "CW";
        var textSize = ImGui.calcTextSize(mark);
        float tx = x0 + (size - textSize.x) * 0.5f;
        float ty = y0 + (size - textSize.y) * 0.5f;
        draw.addText(tx, ty, textCol, mark);
    }

    private boolean shouldDrawHud() {
        if (!enabledInConfig() || !active) return false;
        Minecraft client = Minecraft.getInstance();
        return client != null && client.level != null && client.player != null;
    }

    private int resolveSizePx(float frameW, float frameH) {
        return Math.round(resolveSize(frameW, frameH));
    }

    private float resolveSize(float frameW, float frameH) {
        float scale = watermarkScale();
        float size = frameW * MIN_SIZE_FRAC * scale;
        // Cap by shorter frame edge so tall 9:16 exports keep a readable bug.
        // At scale 7.5 (~7.5/18 ≈ 42% of width) allow up to ~half the short edge.
        float maxByFrame = Math.min(frameW, frameH) * 0.55f;
        float maxSize = Math.min(MAX_SIZE_PX, maxByFrame);
        float minSize = Math.min(MIN_SIZE_PX * Math.max(0.5f, scale), maxSize);
        return Math.max(minSize, Math.min(maxSize, size));
    }

    private float watermarkScale() {
        if (config == null || config.montage == null) return DEFAULT_SCALE;
        double scale = config.montage.exportWatermarkScale;
        if (!Double.isFinite(scale)) return DEFAULT_SCALE;
        return (float) Math.max(0.4, Math.min(7.5, scale));
    }

    private static float clampAlpha(double alpha) {
        if (!Double.isFinite(alpha)) return DEFAULT_ALPHA;
        return (float) Math.max(0.15, Math.min(0.95, alpha));
    }
}
