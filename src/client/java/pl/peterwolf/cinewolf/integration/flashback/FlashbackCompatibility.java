package pl.peterwolf.cinewolf.integration.flashback;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import pl.peterwolf.cinewolf.CineWolfAutoDirector;

import java.util.Optional;

public final class FlashbackCompatibility {
    public static final String SUPPORTED_VERSION = "0.41.1";

    private FlashbackCompatibility() {
    }

    public static Optional<String> detectedVersion() {
        return FabricLoader.getInstance().getModContainer("flashback")
                .map(ModContainer::getMetadata)
                .map(metadata -> metadata.getVersion().getFriendlyString());
    }

    public static boolean isSupportedRuntime() {
        return detectedVersion().map(SUPPORTED_VERSION::equals).orElse(false);
    }

    public static String failureMessage() {
        return detectedVersion()
                .map(version -> "Detected Flashback " + version + "; CineWolf "
                        + CineWolfAutoDirector.VERSION + " supports " + SUPPORTED_VERSION)
                .orElse("Flashback is not installed; CineWolf editor integration is disabled");
    }
}
