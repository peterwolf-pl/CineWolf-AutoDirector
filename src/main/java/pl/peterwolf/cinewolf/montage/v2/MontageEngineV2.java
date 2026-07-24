package pl.peterwolf.cinewolf.montage.v2;

import pl.peterwolf.cinewolf.compatibility.FlashbackCapabilities;
import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisResult;
import pl.peterwolf.cinewolf.montage.event.ReplayEventType;
import pl.peterwolf.cinewolf.montage.event.ScoredReplayEvent;
import pl.peterwolf.cinewolf.montage.plan.DefaultMontagePlanner;
import pl.peterwolf.cinewolf.montage.plan.MontagePlan;
import pl.peterwolf.cinewolf.montage.plan.MontagePlanner;
import pl.peterwolf.cinewolf.montage.plan.MontagePlanningContext;
import pl.peterwolf.cinewolf.montage.plan.MontageRequest;
import pl.peterwolf.cinewolf.montage.plan.MontageWarning;
import pl.peterwolf.cinewolf.shot.ShotGeneratorRegistry;
import pl.peterwolf.cinewolf.vehicle.VehicleProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Montage Engine 2.0: style profiles, narrative phases, diversity, vehicle awareness.
 * Delegates atomic plan construction to the deterministic {@link DefaultMontagePlanner}.
 */
public final class MontageEngineV2 implements MontagePlanner {
    private final MontagePlanner delegate;
    private final NarrativePlanner narrativePlanner = new NarrativePlanner();
    private final DurationAllocator durationAllocator = new DurationAllocator();
    private final ShotDiversityPlanner diversityPlanner = new ShotDiversityPlanner();
    private final VehicleMontagePlanner vehiclePlanner = new VehicleMontagePlanner();
    private final CapabilityAwareShotResolver capabilityResolver;
    private final MontageStyleProfile styleProfile;
    private final VehicleProfile vehicleProfile;
    private final FlashbackCapabilities capabilities;

    public MontageEngineV2() {
        this(new DefaultMontagePlanner(), ShotGeneratorRegistry.createDefault(),
                MontageStyleProfiles.get("clean_cinematic").orElseThrow(),
                null, FlashbackCapabilities.flashback0411());
    }

    public MontageEngineV2(
            MontagePlanner delegate,
            ShotGeneratorRegistry registry,
            MontageStyleProfile styleProfile,
            VehicleProfile vehicleProfile,
            FlashbackCapabilities capabilities
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.capabilityResolver = new CapabilityAwareShotResolver(
                Objects.requireNonNull(registry, "registry"));
        this.styleProfile = Objects.requireNonNull(styleProfile, "styleProfile");
        this.vehicleProfile = vehicleProfile;
        this.capabilities = capabilities == null ? FlashbackCapabilities.flashback0411() : capabilities;
    }

    public MontageEngineV2 withStyle(MontageStyleProfile style) {
        return new MontageEngineV2(delegate, capabilityResolverRegistry(), style, vehicleProfile, capabilities);
    }

    public MontageEngineV2 withVehicle(VehicleProfile vehicle) {
        return new MontageEngineV2(delegate, capabilityResolverRegistry(), styleProfile, vehicle, capabilities);
    }

    public MontageEngineV2 withCapabilities(FlashbackCapabilities caps) {
        return new MontageEngineV2(delegate, capabilityResolverRegistry(), styleProfile, vehicleProfile, caps);
    }

    private ShotGeneratorRegistry capabilityResolverRegistry() {
        // Capability resolver holds registry; recreate default for with* helpers when unknown.
        return ShotGeneratorRegistry.createDefault();
    }

    public MontageStyleProfile styleProfile() {
        return styleProfile;
    }

    public Optional<VehicleProfile> vehicleProfile() {
        return Optional.ofNullable(vehicleProfile);
    }

    public List<NarrativePlanner.PhasedEvent> previewNarrative(ReplayAnalysisResult analysis, MontageRequest request) {
        List<ScoredReplayEvent> ranked = filterWeak(analysis.rankedEvents());
        int minShots = Math.max(1, request.preset().minimumShotCount());
        int maxShots = Math.max(minShots, Math.min(request.maximumPlannedShots(), request.preset().maximumShotCount()));
        int targetShots = Math.max(minShots, Math.min(maxShots, Math.max(1, ranked.size())));
        return narrativePlanner.plan(ranked, targetShots);
    }

    public List<Double> previewDurations(ReplayAnalysisResult analysis, MontageRequest request) {
        List<NarrativePlanner.PhasedEvent> phased = previewNarrative(analysis, request);
        return durationAllocator.allocate(phased, styleProfile, request.outputDurationSeconds(),
                request.minimumShotDuration(), request.maximumShotDuration());
    }

    public List<ShotType> preferredShotsFor(ReplayEventType type) {
        List<ShotType> preferred = new ArrayList<>(styleProfile.preferredShots());
        if (vehicleProfile != null) {
            preferred.addAll(0, vehiclePlanner.templateShots(vehicleProfile, vehicleProfile.state()));
        }
        return capabilityResolver.resolve(preferred, capabilities);
    }

    public ShotType chooseShot(List<ShotType> history, ReplayEventType type, NarrativePhase phase) {
        List<ShotType> candidates = preferredShotsFor(type);
        return diversityPlanner.select(candidates, history, type, phase, styleProfile,
                Set.copyOf(candidates));
    }

    @Override
    public MontagePlan createPlan(ReplayAnalysisResult analysis, MontageRequest request,
                                  MontagePlanningContext context) {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(context, "context");

        List<String> preflight = capabilityResolver.unsupportedReasons(
                List.copyOf(styleProfile.preferredShots()), capabilities);
        MontageRequest adjusted = request;
        if (!styleProfile.preferChronology() && request.preferChronologicalOrder()) {
            // Style may prefer non-chronological, but Flashback path still chronological.
        }
        if (!styleProfile.allowReplaySpeedChanges()) {
            // Delegate already clamps speed; style flag is advisory metadata.
        }
        if (vehicleProfile != null) {
            Optional<String> styleId = vehiclePlanner.recommendedStyleId(vehicleProfile);
            // Keep explicit style; vehicle only contributes shot preference through wrappers.
        }

        MontagePlan plan = delegate.createPlan(analysis, adjusted, context);
        List<MontageWarning> extra = new ArrayList<>();
        for (String reason : preflight) {
            extra.add(MontageWarning.warning(reason));
        }
        for (String reason : vehiclePlanner.planningReasons(vehicleProfile)) {
            extra.add(MontageWarning.info(reason));
        }
        List<ShotType> history = plan.shots().stream().map(shot -> shot.shotType()).collect(Collectors.toList());
        for (String warning : diversityPlanner.diversityWarnings(history)) {
            extra.add(MontageWarning.warning(warning));
        }
        if (extra.isEmpty()) return plan;
        List<MontageWarning> merged = new ArrayList<>(plan.warnings());
        merged.addAll(extra);
        return plan.withWarnings(merged);
    }

    private static List<ScoredReplayEvent> filterWeak(List<ScoredReplayEvent> events) {
        return events.stream()
                .filter(event -> event.event().confidence() >= 0.35 || event.finalScore() >= 0.45)
                .toList();
    }

    public ValidationReport validate(ReplayAnalysisResult analysis, MontageRequest request) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (analysis == null) errors.add("montage.validation.analysis_missing");
        if (request == null) {
            errors.add("montage.validation.request_missing");
            return new ValidationReport(false, errors, warnings);
        }
        if (request.outputDurationSeconds() <= 0) errors.add("montage.validation.duration_invalid");
        if (request.sourceEndReplayTime() <= request.sourceStartReplayTime()) {
            errors.add("montage.validation.source_range_invalid");
        }
        if (!capabilities.supportsMontageWriting()) {
            errors.add("montage.validation.flashback_capabilities");
        }
        if (vehicleProfile == null) {
            warnings.add("montage.validation.vehicle_profile_absent");
        }
        List<ScoredReplayEvent> strong = analysis == null ? List.of() : filterWeak(analysis.rankedEvents());
        if (strong.isEmpty()) warnings.add("montage.validation.no_strong_events");
        return new ValidationReport(errors.isEmpty(), errors, warnings);
    }

    public record ValidationReport(boolean valid, List<String> errors, List<String> warnings) {
        public ValidationReport {
            errors = List.copyOf(errors == null ? List.of() : errors);
            warnings = List.copyOf(warnings == null ? List.of() : warnings);
        }
    }
}
