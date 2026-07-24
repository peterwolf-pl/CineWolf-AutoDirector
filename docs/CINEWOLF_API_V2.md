# CineWolf Integration API 2.0

Package: `pl.peterwolf.cinewolf.api.v2`

Current version: **2.0.0**

## Entry point

Implement `CineWolfIntegration` and register through `CineWolfIntegrationManager`.

```java
public interface CineWolfIntegration {
    String integrationId();
    String displayName();
    String integrationVersion();
    CineWolfApiVersion requiredApiVersion();
    void register(CineWolfRegistrationContext context);
}
```

## Registration context

Providers may register:

- vehicle providers
- cinematic target providers
- replay event detectors
- montage style profile providers
- shot generators (`ShotType` + generator; built-ins cannot be overridden)
- preset providers
- framing providers

## Safety rules

Integrations must **not**:

- write Flashback keyframes outside CineWolf transactions
- remove or silently override built-in IDs
- execute scripts from presets
- load arbitrary classes from imported content
- access networks through CineWolf
- write outside approved CineWolf config directories

Failures isolate to the broken integration only.
