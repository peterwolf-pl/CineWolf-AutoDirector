package pl.peterwolf.cinewolf.api.v2;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.ReplayContext;
import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.model.ShotValidationResult;
import pl.peterwolf.cinewolf.shot.ShotGeneratorRegistry;
import pl.peterwolf.cinewolf.vehicle.VehicleDescriptor;
import pl.peterwolf.cinewolf.vehicle.VehicleProvider;
import pl.peterwolf.cinewolf.vehicle.VehicleProviderRegistry;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class CineWolfIntegrationManagerTest {
    @Test
    void registersValidIntegration() {
        CineWolfIntegrationManager manager = new CineWolfIntegrationManager(
                new VehicleProviderRegistry(), ShotGeneratorRegistry.createDefault());
        IntegrationDiagnostic diagnostic = manager.register(simple("test.mod", new CineWolfApiVersion(2, 0, 0), ctx ->
                ctx.registerVehicleProvider(new VehicleProvider() {
                    @Override
                    public String providerId() {
                        return "test.mod.vehicle";
                    }

                    @Override
                    public int priority() {
                        return 1;
                    }

                    @Override
                    public boolean supports(pl.peterwolf.cinewolf.model.TargetReference t,
                                            pl.peterwolf.cinewolf.model.TargetPose p) {
                        return false;
                    }

                    @Override
                    public Optional<VehicleDescriptor> describe(pl.peterwolf.cinewolf.model.TargetReference t,
                                                                pl.peterwolf.cinewolf.model.TargetPose p) {
                        return Optional.empty();
                    }
                })));
        assertEquals(IntegrationStatus.ACTIVE, diagnostic.status());
        assertEquals(1, diagnostic.registeredVehicles());
    }

    @Test
    void rejectsDuplicateAndIncompatible() {
        CineWolfIntegrationManager manager = CineWolfIntegrationManager.createDefault();
        CineWolfIntegration first = simple("dup.mod", new CineWolfApiVersion(2, 0, 0), ctx -> {
        });
        assertEquals(IntegrationStatus.ACTIVE, manager.register(first).status());
        assertEquals(IntegrationStatus.FAILED, manager.register(first).status());
        assertEquals(IntegrationStatus.INCOMPATIBLE_API,
                manager.register(simple("old.mod", new CineWolfApiVersion(3, 0, 0), ctx -> {
                })).status());
    }

    @Test
    void isolatesFailingProvider() {
        CineWolfIntegrationManager manager = CineWolfIntegrationManager.createDefault();
        AtomicBoolean secondRan = new AtomicBoolean(false);
        IntegrationDiagnostic failed = manager.register(simple("boom.mod", new CineWolfApiVersion(2, 0, 0), ctx -> {
            throw new IllegalStateException("boom");
        }));
        assertEquals(IntegrationStatus.FAILED, failed.status());
        IntegrationDiagnostic ok = manager.register(simple("ok.mod", new CineWolfApiVersion(2, 0, 0),
                ctx -> secondRan.set(true)));
        assertTrue(secondRan.get());
        assertEquals(IntegrationStatus.ACTIVE, ok.status());
        assertFalse(manager.diagnostics().isEmpty());
    }

    @Test
    void blocksBuiltinShotOverride() {
        CineWolfIntegrationManager manager = CineWolfIntegrationManager.createDefault();
        IntegrationDiagnostic diagnostic = manager.register(simple("shots.mod", new CineWolfApiVersion(2, 0, 0), ctx ->
                ctx.registerShotGenerator(ShotType.ORBIT, new pl.peterwolf.cinewolf.api.ShotGenerator() {
                    @Override
                    public CameraPathPlan generate(ShotRequest request, ReplayContext context) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public ShotValidationResult validate(ShotRequest request, ReplayContext context) {
                        return ShotValidationResult.valid();
                    }
                })));
        assertTrue(diagnostic.warnings().stream().anyMatch(w -> w.contains("shot_override_blocked"))
                || diagnostic.errors().stream().anyMatch(e -> e.contains("builtin_override") || e.contains("shot"))
                || diagnostic.registeredShotGenerators() == 0);
    }

    private static CineWolfIntegration simple(String id, CineWolfApiVersion version,
                                              Consumer<CineWolfRegistrationContext> body) {
        return new CineWolfIntegration() {
            @Override
            public String integrationId() {
                return id;
            }

            @Override
            public String displayName() {
                return id;
            }

            @Override
            public String integrationVersion() {
                return "1.0";
            }

            @Override
            public CineWolfApiVersion requiredApiVersion() {
                return version;
            }

            @Override
            public void register(CineWolfRegistrationContext context) {
                body.accept(context);
            }
        };
    }
}
