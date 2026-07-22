package pl.peterwolf.cinewolf.timeline;

/**
 * Investigation result for Flashback 0.41.1 native timeline extension.
 *
 * <p>Flashback 0.41.1 does not expose a stable public API for injecting custom event glyphs,
 * colored markers, or third-party overlays into the native replay timeline. Reflection against
 * internal editor UI classes would be version-fragile and is intentionally not used.</p>
 *
 * <p>Therefore CineWolf 1.3.5 keeps event visualization on {@link CineWolfEventTimelineOverlay}
 * (custom panel mini-timeline) and reports native integration as unavailable.</p>
 */
public final class FlashbackTimelineExtensionAdapter {
    public enum IntegrationMode {
        NATIVE_UNAVAILABLE,
        CUSTOM_OVERLAY
    }

    public IntegrationMode mode() {
        return IntegrationMode.CUSTOM_OVERLAY;
    }

    public boolean nativeTimelineSupported() {
        return false;
    }

    public String statusKey() {
        return "cinewolf.timeline.native_unavailable";
    }

    public String investigationSummary() {
        return "Flashback 0.41.1 has no stable timeline extension API; CineWolf uses a custom event overlay.";
    }
}
