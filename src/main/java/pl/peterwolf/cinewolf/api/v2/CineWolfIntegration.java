package pl.peterwolf.cinewolf.api.v2;

/**
 * Entry point for third-party mods that extend CineWolf AutoDirector.
 * Integrations must not touch Flashback timelines directly.
 */
public interface CineWolfIntegration {
    String integrationId();

    String displayName();

    String integrationVersion();

    CineWolfApiVersion requiredApiVersion();

    void register(CineWolfRegistrationContext context);
}
