package pl.peterwolf.cinewolf.integration.flashback;

import pl.peterwolf.cinewolf.compatibility.CompatibilityStatus;
import pl.peterwolf.cinewolf.compatibility.FlashbackCapabilities;
import pl.peterwolf.cinewolf.compatibility.FlashbackCompatibilityRegistry;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Cached Flashback capability probe. Avoids per-frame work. */
public final class FlashbackCapabilitiesProbe {
    private static final AtomicReference<CompatibilityStatus> CACHE = new AtomicReference<>();

    private FlashbackCapabilitiesProbe() {
    }

    public static CompatibilityStatus current() {
        CompatibilityStatus cached = CACHE.get();
        if (cached != null) return cached;
        CompatibilityStatus status = CompatibilityStatus.from(
                FlashbackCompatibilityRegistry.assess(FlashbackCompatibility.detectedVersion()));
        CACHE.compareAndSet(null, status);
        return CACHE.get();
    }

    public static FlashbackCapabilities capabilities() {
        return current().capabilities();
    }

    public static void invalidate() {
        CACHE.set(null);
    }

    public static boolean editorEnabled() {
        return current().editorIntegrationEnabled();
    }
}
