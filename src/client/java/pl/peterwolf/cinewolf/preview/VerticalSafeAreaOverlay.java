package pl.peterwolf.cinewolf.preview;

import com.moulberry.flashback.editor.ui.ReplayUI;
import imgui.moulberry90.ImDrawList;
import imgui.moulberry90.ImGui;

/** Draws a non-destructive 9:16 composition guide over Flashback's replay viewport. */
public final class VerticalSafeAreaOverlay {
    private volatile boolean visible;
    private volatile double safeFraction = 0.82;

    public void show(double safeFraction) {
        this.safeFraction = Math.max(0.5, Math.min(0.98, safeFraction));
        visible = true;
    }

    public void hide() {
        visible = false;
    }

    public boolean visible() {
        return visible;
    }

    public void render() {
        if (!visible || ReplayUI.frameWidth <= 0 || ReplayUI.frameHeight <= 0) return;
        float frameX = ReplayUI.frameX;
        float frameY = ReplayUI.frameY;
        float frameWidth = ReplayUI.frameWidth;
        float frameHeight = ReplayUI.frameHeight;
        float height = frameHeight * 0.94f;
        float width = height * 9.0f / 16.0f;
        if (width > frameWidth * 0.94f) {
            width = frameWidth * 0.94f;
            height = width * 16.0f / 9.0f;
        }
        float left = frameX + (frameWidth - width) * 0.5f;
        float top = frameY + (frameHeight - height) * 0.5f;
        float right = left + width;
        float bottom = top + height;
        float insetX = (float) (width * (1.0 - safeFraction) * 0.5);
        float insetY = (float) (height * (1.0 - safeFraction) * 0.5);

        ImDrawList draw = ImGui.getForegroundDrawList();
        draw.addRect(left, top, right, bottom, 0xE6FFFFFF, 0.0f, 0, 2.0f);
        draw.addRect(left + insetX, top + insetY, right - insetX, bottom - insetY,
                0xE642F5A7, 0.0f, 0, 1.5f);
        draw.addLine(left + width * 0.5f, top, left + width * 0.5f, bottom, 0x667DFF9B, 1.0f);
        draw.addLine(left, top + height * 0.5f, right, top + height * 0.5f, 0x667DFF9B, 1.0f);
    }
}
