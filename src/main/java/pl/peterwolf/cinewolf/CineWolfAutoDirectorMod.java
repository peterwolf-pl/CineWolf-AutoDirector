package pl.peterwolf.cinewolf;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.peterwolf.cinewolf.api.v2.CineWolfIntegrationManager;
import pl.peterwolf.cinewolf.vehicle.VehicleProviderRegistry;

/**
 * Common initializer. Keeps configuration and integration registration available
 * even when Flashback is missing.
 */
public final class CineWolfAutoDirectorMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(CineWolfAutoDirector.MOD_NAME);
    private static CineWolfIntegrationManager integrationManager;

    @Override
    public void onInitialize() {
        integrationManager = CineWolfIntegrationManager.createDefault();
        LOGGER.info("CineWolf AutoDirector {} common init (API {})",
                CineWolfAutoDirector.VERSION, pl.peterwolf.cinewolf.api.v2.CineWolfApiVersion.CURRENT);
    }

    public static CineWolfIntegrationManager integrations() {
        if (integrationManager == null) {
            integrationManager = CineWolfIntegrationManager.createDefault();
        }
        return integrationManager;
    }

    public static VehicleProviderRegistry vehicles() {
        return integrations().vehicleRegistry();
    }
}
