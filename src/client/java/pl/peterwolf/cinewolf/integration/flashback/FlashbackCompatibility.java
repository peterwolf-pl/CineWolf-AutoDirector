package pl.peterwolf.cinewolf.integration.flashback;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import pl.peterwolf.cinewolf.compatibility.CompatibilityStatus;
import pl.peterwolf.cinewolf.compatibility.FlashbackCapabilities;
import pl.peterwolf.cinewolf.compatibility.FlashbackCompatibilityRegistry;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FlashbackCompatibility {
    public static final String SUPPORTED_VERSION = FlashbackCompatibilityRegistry.SUPPORTED_VERSION;

    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    private FlashbackCompatibility() {
    }

    public static Optional<String> detectedVersion() {
        return FabricLoader.getInstance().getModContainer("flashback")
                .map(ModContainer::getMetadata)
                .map(metadata -> metadata.getVersion().getFriendlyString());
    }

    public static boolean isFlashbackInstalled() {
        return detectedVersion().isPresent();
    }

    public static boolean isSupportedRuntime() {
        return FlashbackCompatibilityRegistry.assess(detectedVersion()).editorIntegrationEnabled();
    }

    public static CompatibilityStatus status() {
        return CompatibilityStatus.from(FlashbackCompatibilityRegistry.assess(detectedVersion()));
    }

    public static FlashbackCapabilities capabilities() {
        return status().capabilities();
    }

    public static String failureMessage() {
        return FlashbackCompatibilityRegistry.assess(detectedVersion()).failureMessage();
    }

    /** Logs the compatibility failure at most once per process. */
    public static void logFailureOnce(org.slf4j.Logger logger) {
        if (LOGGED.compareAndSet(false, true)) {
            logger.error("{}. Supported range: exactly {}.", failureMessage(), SUPPORTED_VERSION);
        }
    }
}
