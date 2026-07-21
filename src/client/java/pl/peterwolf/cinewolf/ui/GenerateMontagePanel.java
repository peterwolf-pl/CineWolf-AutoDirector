package pl.peterwolf.cinewolf.ui;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiHoveredFlags;
import imgui.moulberry90.flag.ImGuiWindowFlags;
import imgui.moulberry90.type.ImFloat;
import imgui.moulberry90.type.ImInt;
import net.minecraft.client.resources.language.I18n;
import org.slf4j.Logger;
import pl.peterwolf.cinewolf.api.ReplayEditorAdapter;
import pl.peterwolf.cinewolf.config.CineWolfConfig;
import pl.peterwolf.cinewolf.config.CineWolfConfigManager;
import pl.peterwolf.cinewolf.config.MontageConfig;
import pl.peterwolf.cinewolf.integration.flashback.FlashbackMontageTimelineWriter;
import pl.peterwolf.cinewolf.integration.flashback.FlashbackReplayEditorAdapter;
import pl.peterwolf.cinewolf.model.EasingType;
import pl.peterwolf.cinewolf.model.RotationDirection;
import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.montage.MontageAnalysisController;
import pl.peterwolf.cinewolf.montage.MontageGenerationController;
import pl.peterwolf.cinewolf.montage.MontagePreviewController;
import pl.peterwolf.cinewolf.montage.analysis.AnalysisWarning;
import pl.peterwolf.cinewolf.montage.event.ScoredReplayEvent;
import pl.peterwolf.cinewolf.montage.event.EventEvidence;
import pl.peterwolf.cinewolf.montage.plan.MontagePlan;
import pl.peterwolf.cinewolf.montage.plan.MontagePlanEditor;
import pl.peterwolf.cinewolf.montage.plan.MontageWarning;
import pl.peterwolf.cinewolf.montage.plan.PlannedMontageShot;
import pl.peterwolf.cinewolf.montage.preset.MontagePacing;
import pl.peterwolf.cinewolf.montage.preset.MontagePreset;
import pl.peterwolf.cinewolf.montage.preset.MontagePresetRegistry;
import pl.peterwolf.cinewolf.montage.preset.MontagePresetType;
import pl.peterwolf.cinewolf.montage.preset.OutputAspectRatio;
import pl.peterwolf.cinewolf.montage.project.MontageDebugExport;
import pl.peterwolf.cinewolf.montage.project.MontageProject;
import pl.peterwolf.cinewolf.montage.project.MontageProjectManager;
import pl.peterwolf.cinewolf.montage.timeline.MontageTimelineInterval;
import pl.peterwolf.cinewolf.montage.timeline.MontageTimelineWriteOptions;
import pl.peterwolf.cinewolf.montage.ui.PendingMontageAction;
import pl.peterwolf.cinewolf.preview.VerticalSafeAreaOverlay;
import pl.peterwolf.cinewolf.preview.PreviewController;
import pl.peterwolf.cinewolf.model.PathWarning;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Separate UI component for the v1.2 analysis, montage editor, preview, and write workflow. */
public final class GenerateMontagePanel {
    private static final int TOOLTIP_HOVER_FLAGS = ImGuiHoveredFlags.ForTooltip
            | ImGuiHoveredFlags.Stationary | ImGuiHoveredFlags.DelayNormal | ImGuiHoveredFlags.NoSharedDelay;
    private final FlashbackReplayEditorAdapter adapter;
    private final CineWolfConfigManager configManager;
    private final CineWolfConfig config;
    private final MontageAnalysisController analysisController;
    private final MontageGenerationController generationController;
    private final MontagePreviewController previewController;
    private final VerticalSafeAreaOverlay safeAreaOverlay;
    private final PreviewController singleShotPreview;
    private final MontagePresetRegistry presets = MontagePresetRegistry.createDefault();
    private final MontagePlanEditor planEditor = new MontagePlanEditor();
    private final MontageProjectManager projectManager = new MontageProjectManager();
    private final Logger logger;
    private final ImFloat floatValue = new ImFloat();
    private final ImInt comboValue = new ImInt();

    private UUID selectedEventId;
    private boolean analysisWhenPreviewRestored;
    private PendingMontageAction pendingAction;
    private FlashbackMontageTimelineWriter.InspectionResult pendingInspection;
    private MontageTimelineInterval pendingReplaceInterval;
    private List<ActionNotice> actionNotices = List.of();
    private final List<MontageProject.ManualEditSummary> manualEdits = new ArrayList<>();
    private long actionNoticesUntil;

    public GenerateMontagePanel(FlashbackReplayEditorAdapter adapter, CineWolfConfigManager configManager,
                                MontageAnalysisController analysisController,
                                MontageGenerationController generationController,
                                MontagePreviewController previewController,
                                VerticalSafeAreaOverlay safeAreaOverlay, PreviewController singleShotPreview,
                                Logger logger) {
        this.adapter = adapter;
        this.configManager = configManager;
        this.config = configManager.get();
        this.analysisController = analysisController;
        this.generationController = generationController;
        this.previewController = previewController;
        this.safeAreaOverlay = safeAreaOverlay;
        this.singleShotPreview = singleShotPreview;
        this.logger = logger;
    }

    public void render(TargetReference sharedTarget, List<ReplayEditorAdapter.ReplayEntityDescriptor> entities) {
        invalidateChangedScope(sharedTarget);
        processDeferredActions(sharedTarget);
        renderRange();
        renderPresetAndOutput();
        renderCoreSettings(sharedTarget);
        renderAdvanced();
        renderAnalysisActions(sharedTarget);
        renderAnalysisProgress();
        renderEventTimeline();
        renderShotEditor(entities, sharedTarget);
        renderPreviewAndGeneration();
        renderConflictDialog();
        renderMessages();
        updateSafeAreaOverlay();
    }

    private void invalidateChangedScope(TargetReference sharedTarget) {
        if (analysisController.phase() == MontageAnalysisController.Phase.RESTORING) return;
        var request = analysisController.currentRequest();
        if (request == null) return;
        ReplayEditorAdapter.ReplayTimeRange range = adapter.getSelectedTimeRange();
        java.util.Set<UUID> requestedTargets = request.selectedTargets().stream()
                .map(TargetReference::uuid).collect(java.util.stream.Collectors.toSet());
        java.util.Set<UUID> currentTargets = sharedTarget == null ? java.util.Set.of()
                : java.util.Set.of(sharedTarget.uuid());
        boolean changed = !range.selected() || range.startTick() != request.startReplayTime()
                || range.endTick() != request.endReplayTime() || !requestedTargets.equals(currentTargets);
        if (!changed) return;

        manualEdits.clear();
        clearPendingAction();
        analysisWhenPreviewRestored = false;
        selectedEventId = null;
        previewController.exit();
        generationController.clear();
        analysisController.clear();
        showAction(tr("cinewolf.montage.settings.scope_changed"), NoticeSeverity.WARNING);
    }

    private void renderRange() {
        ImGui.separatorText(tr("cinewolf.montage.section.range"));
        ReplayEditorAdapter.ReplayTimeRange range = adapter.getSelectedTimeRange();
        if (!range.selected()) {
            ImGui.textColored(255, 100, 100, 255, tr("cinewolf.montage.range.required"));
            return;
        }
        ImGui.textUnformatted(tr("cinewolf.montage.range.value", timestamp(range.startTick()),
                timestamp(range.endTick()), format((range.endTick() - range.startTick()) / 20.0)));
    }

    private void renderPresetAndOutput() {
        MontageConfig settings = config.montage;
        MontagePresetType[] types = MontagePresetType.values();
        comboValue.set(settings.presetType.ordinal());
        if (ImGui.combo(tr("cinewolf.montage.field.preset"), comboValue,
                Arrays.stream(types).map(type -> preset(type).displayNameKey()).map(GenerateMontagePanel::tr)
                        .toArray(String[]::new))) {
            settings.presetType = types[comboValue.get()];
            settings.applyPreset(preset(settings.presetType));
            settingsChanged();
        }
        tooltip(tr("cinewolf.montage.tooltip.preset"));
        if (number(tr("cinewolf.montage.field.output_duration"), settings.outputDurationSeconds,
                1.0, 1.0, 3600.0, tr("cinewolf.montage.tooltip.output_duration"),
                value -> settings.outputDurationSeconds = value)) settingsChanged();

        OutputAspectRatio[] ratios = OutputAspectRatio.values();
        comboValue.set(settings.aspectRatio.ordinal());
        if (ImGui.combo(tr("cinewolf.montage.field.aspect_ratio"), comboValue,
                Arrays.stream(ratios).map(value -> tr("cinewolf.montage.aspect."
                        + value.name().toLowerCase(Locale.ROOT))).toArray(String[]::new))) {
            settings.aspectRatio = ratios[comboValue.get()];
            settingsChanged();
        }
        tooltip(tr("cinewolf.montage.tooltip.aspect_ratio"));
    }

    private void renderCoreSettings(TargetReference sharedTarget) {
        MontageConfig settings = config.montage;
        String targetText = sharedTarget == null ? tr("cinewolf.montage.target.automatic")
                : sharedTarget.displayName() + " • " + sharedTarget.shortIdentifier();
        ImGui.textUnformatted(tr("cinewolf.montage.field.main_target") + ": " + targetText);
        boolean automatic = settings.automaticTargetDetection;
        if (ImGui.checkbox(tr("cinewolf.montage.field.automatic_target"), automatic)) {
            settings.automaticTargetDetection = !automatic;
            settingsChanged();
        }
        tooltip(tr("cinewolf.montage.tooltip.automatic_target"));

        MontagePacing[] pacing = MontagePacing.values();
        comboValue.set(settings.pacing.ordinal());
        if (ImGui.combo(tr("cinewolf.montage.field.pacing"), comboValue,
                Arrays.stream(pacing).map(value -> tr("cinewolf.montage.pacing."
                        + value.name().toLowerCase(Locale.ROOT))).toArray(String[]::new))) {
            settings.pacing = pacing[comboValue.get()];
            settingsChanged();
        }
        tooltip(tr("cinewolf.montage.tooltip.pacing"));
        boolean changed = false;
        changed |= number(tr("cinewolf.montage.field.movement_intensity"), settings.cameraMovementIntensity,
                0.05, 0.0, 1.0, tr("cinewolf.montage.tooltip.movement_intensity"),
                value -> settings.cameraMovementIntensity = value);
        changed |= number(tr("cinewolf.montage.field.cut_frequency"), settings.cutFrequency,
                0.05, 0.0, 1.0, tr("cinewolf.montage.tooltip.cut_frequency"),
                value -> settings.cutFrequency = value);
        changed |= number(tr("cinewolf.montage.field.minimum_shot"), settings.minimumShotDuration,
                0.25, 0.5, 120.0, tr("cinewolf.montage.tooltip.minimum_shot"),
                value -> settings.minimumShotDuration = value);
        changed |= number(tr("cinewolf.montage.field.maximum_shot"), settings.maximumShotDuration,
                0.25, settings.minimumShotDuration, 300.0, tr("cinewolf.montage.tooltip.maximum_shot"),
                value -> settings.maximumShotDuration = value);
        changed |= number(tr("cinewolf.montage.field.event_sensitivity"), settings.eventSensitivity,
                0.05, 0.0, 1.0, tr("cinewolf.montage.tooltip.event_sensitivity"),
                value -> settings.eventSensitivity = value);
        if (changed) settingsChanged();
    }

    private void renderAdvanced() {
        boolean expanded = ImGui.collapsingHeader(tr("cinewolf.montage.section.advanced"));
        tooltip(tr("cinewolf.montage.tooltip.advanced"));
        if (!expanded) return;
        MontageConfig settings = config.montage;
        boolean changed = false;
        changed |= toggle("cinewolf.montage.field.include_markers", settings.includeReplayMarkers,
                "cinewolf.montage.tooltip.include_markers",
                value -> settings.includeReplayMarkers = value);
        changed |= toggle("cinewolf.montage.field.include_combat", settings.includeCombat,
                "cinewolf.montage.tooltip.include_combat",
                value -> settings.includeCombat = value);
        changed |= toggle("cinewolf.montage.field.include_building", settings.includeBuildingEvents,
                "cinewolf.montage.tooltip.include_building",
                value -> settings.includeBuildingEvents = value);
        changed |= toggle("cinewolf.montage.field.include_vehicles", settings.includeVehicles,
                "cinewolf.montage.tooltip.include_vehicles",
                value -> settings.includeVehicles = value);
        changed |= toggle("cinewolf.montage.field.include_flight", settings.includeFlight,
                "cinewolf.montage.tooltip.include_flight",
                value -> settings.includeFlight = value);
        changed |= toggle("cinewolf.montage.field.speed_changes", settings.allowReplaySpeedChanges,
                "cinewolf.montage.tooltip.speed_changes",
                value -> settings.allowReplaySpeedChanges = value);
        changed |= toggle("cinewolf.montage.field.chronological", settings.preferChronologicalOrder,
                "cinewolf.montage.tooltip.chronological",
                value -> settings.preferChronologicalOrder = value);
        changed |= toggle("cinewolf.montage.field.collision", settings.collisionAvoidance,
                "cinewolf.montage.tooltip.collision",
                value -> settings.collisionAvoidance = value);

        renderPathSmoothingFields();

        changed |= number(tr("cinewolf.montage.field.coarse_rate"), settings.coarseSamplesPerSecond,
                1.0, 2.0, 5.0, tr("cinewolf.montage.tooltip.coarse_rate"),
                value -> settings.coarseSamplesPerSecond = (int) value);
        changed |= number(tr("cinewolf.montage.field.detailed_rate"), settings.detailedSamplesPerSecond,
                1.0, 10.0, 20.0, tr("cinewolf.montage.tooltip.detailed_rate"),
                value -> settings.detailedSamplesPerSecond = (int) value);
        changed |= number(tr("cinewolf.montage.field.minimum_speed"), settings.minimumReplaySpeed,
                0.05, 0.05, 20.0, tr("cinewolf.montage.tooltip.minimum_speed"),
                value -> settings.minimumReplaySpeed = value);
        changed |= number(tr("cinewolf.montage.field.maximum_speed"), settings.maximumReplaySpeed,
                0.05, settings.minimumReplaySpeed, 40.0, tr("cinewolf.montage.tooltip.maximum_speed"),
                value -> settings.maximumReplaySpeed = value);
        changed |= number(tr("cinewolf.montage.field.safe_area"), settings.verticalSafeArea,
                0.01, 0.5, 0.98, tr("cinewolf.montage.tooltip.safe_area"),
                value -> settings.verticalSafeArea = value);
        changed |= toggle("cinewolf.montage.field.debug_json", settings.debugJsonExport,
                "cinewolf.montage.tooltip.debug_json",
                value -> settings.debugJsonExport = value);
        if (changed) settingsChanged();

        if (ImGui.button(tr("cinewolf.montage.action.save_project"))) saveProject(false);
        tooltip(tr("cinewolf.montage.tooltip.save_project"));
        ImGui.sameLine();
        if (ImGui.button(tr("cinewolf.montage.action.export_debug"))) saveProject(true);
        tooltip(tr("cinewolf.montage.tooltip.export_debug"));
    }

    private void renderPathSmoothingFields() {
        ImGui.separatorText(tr("cinewolf.section.path_smoothing"));
        boolean changed = false;

        boolean enabled = config.pathSmoothing.enabled;
        if (ImGui.checkbox(tr("cinewolf.field.path_smoothing_enabled") + "###montage-path-smoothing", enabled)) {
            config.pathSmoothing.enabled = !enabled;
            changed = true;
        }
        tooltip(tr("cinewolf.tooltip.path_smoothing_enabled"));

        boolean smoothingDisabled = !config.pathSmoothing.enabled;
        int smoothingTooltipFlags = smoothingDisabled ? ImGuiHoveredFlags.AllowWhenDisabled : ImGuiHoveredFlags.None;
        if (smoothingDisabled) ImGui.beginDisabled();
        changed |= number(tr("cinewolf.field.position_smoothing_strength") + "###montage-position-smoothing",
                config.pathSmoothing.positionStrength, 0.05, 0.0, 1.0,
                tr("cinewolf.tooltip.position_smoothing_strength"), smoothingTooltipFlags,
                value -> config.pathSmoothing.positionStrength = value);
        changed |= number(tr("cinewolf.field.rotation_smoothing_strength") + "###montage-rotation-smoothing",
                config.pathSmoothing.rotationStrength, 0.05, 0.0, 1.0,
                tr("cinewolf.tooltip.rotation_smoothing_strength"), smoothingTooltipFlags,
                value -> config.pathSmoothing.rotationStrength = value);
        changed |= number(tr("cinewolf.field.smoothing_window") + "###montage-smoothing-window",
                config.pathSmoothing.windowSeconds, 0.05, 0.05, 2.0,
                tr("cinewolf.tooltip.smoothing_window"), smoothingTooltipFlags,
                value -> config.pathSmoothing.windowSeconds = value);

        boolean rejectOutliers = config.pathSmoothing.outlierRejection;
        if (ImGui.checkbox(tr("cinewolf.field.outlier_rejection") + "###montage-outlier-rejection",
                rejectOutliers)) {
            config.pathSmoothing.outlierRejection = !rejectOutliers;
            changed = true;
        }
        tooltip(tr("cinewolf.tooltip.outlier_rejection"), smoothingTooltipFlags);
        if (smoothingDisabled) ImGui.endDisabled();

        boolean outlierFieldsDisabled = smoothingDisabled || !config.pathSmoothing.outlierRejection;
        int outlierTooltipFlags = outlierFieldsDisabled
                ? ImGuiHoveredFlags.AllowWhenDisabled : ImGuiHoveredFlags.None;
        if (outlierFieldsDisabled) ImGui.beginDisabled();
        changed |= number(tr("cinewolf.field.outlier_distance") + "###montage-outlier-distance",
                config.pathSmoothing.outlierThresholdBlocks, 0.25, 0.25, 64.0,
                tr("cinewolf.tooltip.outlier_distance"), outlierTooltipFlags,
                value -> config.pathSmoothing.outlierThresholdBlocks = value);
        changed |= number(tr("cinewolf.field.outlier_speed") + "###montage-outlier-speed",
                config.pathSmoothing.outlierSpeedThresholdBlocksPerSecond,
                1.0, 1.0, 512.0, tr("cinewolf.tooltip.outlier_speed"), outlierTooltipFlags,
                value -> config.pathSmoothing.outlierSpeedThresholdBlocksPerSecond = value);
        if (outlierFieldsDisabled) ImGui.endDisabled();

        if (changed) pathSettingsChanged();
    }

    private void renderAnalysisActions(TargetReference sharedTarget) {
        ImGui.separatorText(tr("cinewolf.montage.section.analysis"));
        if (!analysisController.busy()) {
            if (ImGui.button(tr("cinewolf.montage.action.analyze"))) {
                manualEdits.clear();
                clearPendingAction();
                singleShotPreview.clear();
                generationController.clear();
                if (previewController.active()) {
                    previewController.exit();
                }
                if (!seekControllersIdle()) {
                    analysisWhenPreviewRestored = true;
                    showAction(tr("cinewolf.montage.preview.wait_restore"), NoticeSeverity.INFO);
                } else {
                    analysisController.start(sharedTarget);
                }
            }
            tooltip(tr("cinewolf.montage.tooltip.analyze"));
        } else {
            ImGui.beginDisabled();
            ImGui.button(tr("cinewolf.montage.action.analyze"));
            ImGui.endDisabled();
            tooltip(tr("cinewolf.montage.tooltip.analyze"), ImGuiHoveredFlags.AllowWhenDisabled);
            ImGui.sameLine();
            if (ImGui.button(tr("cinewolf.montage.action.cancel_analysis"))) {
                clearPendingAction();
                analysisController.cancel();
            }
            tooltip(tr("cinewolf.montage.tooltip.cancel_analysis"));
        }
        ImGui.sameLine();
        if (ImGui.button(tr("cinewolf.montage.action.clear_analysis"))) {
            manualEdits.clear();
            clearPendingAction();
            previewController.exit();
            generationController.clear();
            analysisController.clear();
            analysisWhenPreviewRestored = false;
            selectedEventId = null;
        }
        tooltip(tr("cinewolf.montage.tooltip.clear_analysis"));
    }

    private void renderAnalysisProgress() {
        if (analysisController.busy()) {
            ImGui.progressBar(analysisController.progress(), -1, 0,
                    localizedStatus(analysisController.statusKey(), analysisController.statusArguments()));
        } else {
            ImGui.textWrapped(localizedStatus(analysisController.statusKey(), analysisController.statusArguments()));
        }
        var result = analysisController.analysisResult();
        if (result != null) {
            ImGui.textDisabled(tr("cinewolf.montage.analysis.summary", result.statistics().analyzedSampleCount(),
                    result.statistics().entityCount(), result.statistics().mergedEventCount(),
                    result.statistics().sceneCount()));
            for (AnalysisWarning warning : result.warnings()) {
                int color = warning.severity() == AnalysisWarning.Severity.ERROR ? 0xFFFF5555
                        : warning.severity() == AnalysisWarning.Severity.INFO ? 0xFF75D2FF : 0xFFFFB347;
                ImGui.textColored(color, localizeCode(warning.code(), warning.arguments()));
            }
        }
    }

    private void renderEventTimeline() {
        var result = analysisController.analysisResult();
        if (result == null || result.rankedEvents().isEmpty()) return;
        ImGui.separatorText(tr("cinewolf.montage.section.events"));
        int shown = 0;
        for (ScoredReplayEvent scored : result.rankedEvents()) {
            if (shown++ >= 64) break;
            String id = scored.event().eventId().toString();
            String label = tr("cinewolf.event." + scored.event().type().name().toLowerCase(Locale.ROOT))
                    + " " + timestamp(scored.event().peakReplayTime()) + "  " + format(scored.finalScore())
                    + "###event-" + id;
            double intensity = Math.max(0.0, Math.min(1.0, scored.finalScore()));
            int markerColor = 0xFF000000 | ((int) (120 + intensity * 135) << 16)
                    | ((int) (170 - intensity * 60) << 8) | (int) (230 - intensity * 170);
            ImGui.textColored(markerColor, "●");
            ImGui.sameLine();
            if (ImGui.selectable(label, scored.event().eventId().equals(selectedEventId))) {
                selectedEventId = scored.event().eventId();
                adapter.setReplayPaused(true);
                adapter.goToReplayTick(scored.event().peakReplayTime());
            }
            tooltip(tr("cinewolf.montage.tooltip.event_seek"));
        }
        result.rankedEvents().stream().filter(value -> value.event().eventId().equals(selectedEventId))
                .findFirst().ifPresent(this::renderScoreDetails);
    }

    private void renderScoreDetails(ScoredReplayEvent event) {
        var replayEvent = event.event();
        ImGui.textWrapped(tr("cinewolf.montage.event.details", format(replayEvent.confidence()),
                format(replayEvent.magnitude()), timestamp(replayEvent.startReplayTime()),
                timestamp(replayEvent.endReplayTime())));
        String targets = replayEvent.targets().isEmpty() ? tr("cinewolf.montage.event.no_targets")
                : replayEvent.targets().stream().map(target -> target.displayName() + " ("
                        + target.shortIdentifier() + ")").sorted().collect(java.util.stream.Collectors.joining(", "));
        ImGui.textWrapped(tr("cinewolf.montage.event.targets", targets));
        ImGui.textWrapped(tr("cinewolf.montage.event.sources", replayEvent.evidence().sources().stream()
                .map(source -> tr("cinewolf.montage.event.source."
                        + source.name().toLowerCase(Locale.ROOT))).sorted()
                .collect(java.util.stream.Collectors.joining(", "))));
        for (EventEvidence.Measurement measurement : replayEvent.evidence().measurements()) {
            String name = localizeEvidenceToken("measurement", measurement.name());
            String unit = localizeEvidenceToken("unit", measurement.unit());
            if (measurement.comparison() == EventEvidence.Comparison.NONE) {
                ImGui.bulletText(tr("cinewolf.montage.event.measurement_observed", name,
                        format(measurement.value()), unit));
            } else {
                String comparison = tr("cinewolf.montage.event.comparison."
                        + measurement.comparison().name().toLowerCase(Locale.ROOT));
                ImGui.bulletText(tr("cinewolf.montage.event.measurement_threshold", name,
                        format(measurement.value()), unit, comparison,
                        format(measurement.threshold())));
            }
        }
        for (EventEvidence.Attribute attribute : replayEvent.evidence().attributes()) {
            ImGui.bulletText(tr("cinewolf.montage.event.attribute",
                    localizeEvidenceToken("attribute", attribute.name()),
                    localizeEvidenceToken("attribute_value", attribute.value())));
        }
        if (!replayEvent.evidence().relatedTypes().isEmpty()) {
            ImGui.textWrapped(tr("cinewolf.montage.event.related", replayEvent.evidence().relatedTypes().stream()
                    .map(type -> tr("cinewolf.event." + type.name().toLowerCase(Locale.ROOT))).sorted()
                    .collect(java.util.stream.Collectors.joining(", "))));
        }
        ImGui.textWrapped(tr("cinewolf.montage.event.score_components", format(event.importanceScore()),
                format(event.cinematicScore()), format(event.uniquenessScore()),
                format(event.presetCompatibilityScore()), format(event.finalScore())));
        for (String reason : event.scoringReasons()) ImGui.bulletText(localizeCode(reason));
    }

    private void renderShotEditor(List<ReplayEditorAdapter.ReplayEntityDescriptor> entities,
                                  TargetReference sharedTarget) {
        MontagePlan plan = analysisController.montagePlan();
        if (plan == null) return;
        ImGui.separatorText(tr("cinewolf.montage.section.shots"));
        ImGui.textDisabled(tr("cinewolf.montage.plan.summary", plan.shots().size(),
                format(plan.statistics().plannedOutputDurationSeconds()),
                format(plan.statistics().shotDiversityScore())));
        for (MontageWarning warning : plan.warnings()) {
            int color = warning.severity() == MontageWarning.Severity.ERROR ? 0xFFFF5555 : 0xFFFFB347;
            ImGui.textColored(color, localizeCode(warning.code(), warning.arguments()));
        }
        List<PlannedMontageShot> snapshot = plan.shots();
        for (PlannedMontageShot shot : snapshot) renderShot(plan, shot, entities, sharedTarget);

        if (ImGui.button(tr("cinewolf.montage.action.regenerate_unlocked"))) {
            clearPendingAction();
            previewController.exit();
            generationController.clear();
            analysisController.regeneratePlan(sharedTarget);
        }
        tooltip(tr("cinewolf.montage.tooltip.regenerate_unlocked"));
    }

    private void renderShot(MontagePlan plan, PlannedMontageShot shot,
                            List<ReplayEditorAdapter.ReplayEntityDescriptor> entities,
                            TargetReference sharedTarget) {
        boolean selectedSource = shot.sourceEvent().eventId().equals(selectedEventId);
        String title = (selectedSource ? "★ " : "") + (shot.order() + 1) + ". " + tr("cinewolf.shot."
                + shot.shotType().name().toLowerCase(Locale.ROOT)) + " • "
                + timestamp(shot.sourceEvent().peakReplayTime()) + " • "
                + tr("cinewolf.event." + shot.sourceEvent().type().name().toLowerCase(Locale.ROOT))
                + "###shot-" + shot.shotId();
        boolean shotExpanded = ImGui.treeNode(title);
        tooltip(tr("cinewolf.montage.tooltip.shot_expand"));
        if (!shotExpanded) return;
        boolean enabled = shot.enabled();
        if (ImGui.checkbox(tr("cinewolf.montage.shot.enabled") + "###enabled-" + shot.shotId(), enabled)) {
            updatePlan(planEditor.setEnabled(plan, shot.shotId(), !enabled));
            plan = analysisController.montagePlan();
        }
        tooltip(tr("cinewolf.montage.tooltip.shot_enabled"));
        ImGui.sameLine();
        boolean locked = shot.locked();
        if (ImGui.checkbox(tr("cinewolf.montage.shot.locked") + "###locked-" + shot.shotId(), locked)) {
            updatePlan(planEditor.setLocked(plan, shot.shotId(), !locked));
            plan = analysisController.montagePlan();
        }
        tooltip(tr("cinewolf.montage.tooltip.shot_locked"));
        if (ImGui.smallButton(tr("cinewolf.montage.shot.up") + "###up-" + shot.shotId())) {
            updatePlan(planEditor.move(plan, shot.shotId(), -1));
        }
        tooltip(tr("cinewolf.montage.tooltip.shot_up"));
        ImGui.sameLine();
        if (ImGui.smallButton(tr("cinewolf.montage.shot.down") + "###down-" + shot.shotId())) {
            updatePlan(planEditor.move(plan, shot.shotId(), 1));
        }
        tooltip(tr("cinewolf.montage.tooltip.shot_down"));
        ImGui.sameLine();
        if (ImGui.smallButton(tr("cinewolf.montage.shot.duplicate") + "###duplicate-" + shot.shotId())) {
            updatePlan(planEditor.duplicate(plan, shot.shotId()));
        }
        tooltip(tr("cinewolf.montage.tooltip.shot_duplicate"));
        ImGui.sameLine();
        if (ImGui.smallButton(tr("cinewolf.montage.shot.remove") + "###remove-" + shot.shotId())) {
            updatePlan(planEditor.remove(plan, shot.shotId()));
            ImGui.treePop();
            return;
        }
        tooltip(tr("cinewolf.montage.tooltip.shot_remove"));

        ImGui.textDisabled(tr("cinewolf.montage.shot.details", format(shot.sourceEventScore()),
                shot.target().displayName(), format(shot.outputDurationSeconds()), format(shot.replaySpeed()),
                tr("cinewolf.framing." + shot.framing().name().toLowerCase(Locale.ROOT)), shot.warnings().size()));

        ShotType[] types = ShotType.values();
        comboValue.set(shot.shotType().ordinal());
        if (ImGui.combo(tr("cinewolf.montage.shot.replace_type") + "###type-" + shot.shotId(), comboValue,
                Arrays.stream(types).map(value -> tr("cinewolf.shot." + value.name().toLowerCase(Locale.ROOT)))
                        .toArray(String[]::new))) {
            ShotType replacement = types[comboValue.get()];
            updatePlan(planEditor.replaceRequest(plan, shot.shotId(), copyRequest(shot.shotRequest(),
                    shot.target(), replacement), shot.framing(), List.of("montage.reason.manual_shot_type")));
        }
        tooltip(tr("cinewolf.montage.tooltip.shot_replace_type"));

        List<TargetReference> targets = availableTargets(entities, sharedTarget, shot.target());
        int targetIndex = Math.max(0, targets.indexOf(shot.target()));
        comboValue.set(targetIndex);
        if (ImGui.combo(tr("cinewolf.montage.shot.change_target") + "###target-" + shot.shotId(), comboValue,
                targets.stream().map(target -> target.displayName() + " • " + target.shortIdentifier())
                        .toArray(String[]::new))) {
            TargetReference replacement = targets.get(comboValue.get());
            updatePlan(planEditor.replaceRequest(plan, shot.shotId(), copyRequest(shot.shotRequest(),
                    replacement, shot.shotType()), shot.framing(), List.of("montage.reason.manual_target")));
        }
        tooltip(tr("cinewolf.montage.tooltip.shot_change_target"));

        boolean parametersExpanded = ImGui.treeNode(
                tr("cinewolf.montage.shot.edit_parameters") + "###params-" + shot.shotId());
        tooltip(tr("cinewolf.montage.tooltip.shot_edit_parameters"));
        if (parametersExpanded) {
            editShotParameters(plan, shot);
            ImGui.treePop();
        }
        if (!shot.enabled()) ImGui.beginDisabled();
        if (ImGui.smallButton(tr("cinewolf.montage.shot.preview") + "###preview-" + shot.shotId())) {
            requestShotPreview(plan, shot.shotId());
        }
        tooltip(tr("cinewolf.montage.tooltip.shot_preview"),
                shot.enabled() ? ImGuiHoveredFlags.None : ImGuiHoveredFlags.AllowWhenDisabled);
        if (!shot.enabled()) ImGui.endDisabled();
        for (String reason : shot.planningReasons().stream().limit(4).toList()) {
            ImGui.bulletText(localizeCode(reason));
        }
        for (MontageWarning warning : shot.warnings()) {
            int color = warning.severity() == MontageWarning.Severity.ERROR ? 0xFFFF5555
                    : warning.severity() == MontageWarning.Severity.INFO ? 0xFF75D2FF : 0xFFFFB347;
            ImGui.textColored(color, localizeCode(warning.code(), warning.arguments()));
        }
        ImGui.treePop();
    }

    private void editShotParameters(MontagePlan plan, PlannedMontageShot shot) {
        ShotRequest request = shot.shotRequest();
        double[] values = {request.height(), request.distance(), request.diameter(), request.fov(),
                request.cameraSpeed(), request.lookAheadSeconds(), request.startDistance(), request.endDistance(),
                request.rpm(), request.startAngleDegrees()};
        RotationDirection[] direction = {request.direction()};
        EasingType[] easing = {request.easing()};
        boolean changed = false;
        changed |= number(tr("cinewolf.field.height") + "###height-" + shot.shotId(), values[0], 0.25,
                -64, 256, tr("cinewolf.tooltip.height"), value -> values[0] = value);
        if (request.shotType() == ShotType.FOLLOW || request.shotType() == ShotType.FLYBY) {
            changed |= number(tr("cinewolf.field.distance") + "###distance-" + shot.shotId(), values[1], 0.25,
                    0.25, 512, tr("cinewolf.tooltip.distance"), value -> values[1] = value);
            changed |= number(tr("cinewolf.field.camera_speed") + "###speed-" + shot.shotId(), values[4], 0.25,
                    0.05, 128, tr("cinewolf.tooltip.camera_speed"), value -> values[4] = value);
        }
        if (request.shotType() == ShotType.ORBIT) {
            changed |= number(tr("cinewolf.field.diameter") + "###diameter-" + shot.shotId(), values[2], 0.25,
                    0.5, 512, tr("cinewolf.tooltip.diameter"), value -> values[2] = value);
            changed |= number(tr("cinewolf.field.rpm") + "###rpm-" + shot.shotId(), values[8], 0.05,
                    0, 60, tr("cinewolf.tooltip.rpm"), value -> values[8] = value);
            changed |= number(tr("cinewolf.field.start_angle") + "###angle-" + shot.shotId(), values[9], 5,
                    -3600, 3600, tr("cinewolf.tooltip.start_angle"), value -> values[9] = value);
        }
        if (request.shotType() == ShotType.DOLLY_IN || request.shotType() == ShotType.DOLLY_OUT) {
            changed |= number(tr("cinewolf.field.start_distance") + "###start-distance-" + shot.shotId(),
                    values[6], 0.25, 1, 512, tr("cinewolf.tooltip.start_distance"),
                    value -> values[6] = value);
            changed |= number(tr("cinewolf.field.end_distance") + "###end-distance-" + shot.shotId(),
                    values[7], 0.25, 1, 512, tr("cinewolf.tooltip.end_distance"),
                    value -> values[7] = value);
        }
        changed |= number(tr("cinewolf.field.fov") + "###fov-" + shot.shotId(), values[3], 1,
                1, 110, tr("cinewolf.tooltip.fov"), value -> values[3] = value);
        if (request.shotType() != ShotType.FLYBY) {
            changed |= number(tr("cinewolf.field.look_ahead") + "###look-" + shot.shotId(), values[5], 0.05,
                    0, 10, tr("cinewolf.tooltip.look_ahead"), value -> values[5] = value);
        }

        RotationDirection[] directions = request.shotType() == ShotType.FLYBY
                ? new RotationDirection[]{RotationDirection.LEFT_TO_RIGHT, RotationDirection.RIGHT_TO_LEFT}
                : new RotationDirection[]{RotationDirection.CLOCKWISE, RotationDirection.COUNTERCLOCKWISE};
        int directionIndex = Math.max(0, Arrays.asList(directions).indexOf(direction[0]));
        comboValue.set(directionIndex);
        if (ImGui.combo(tr("cinewolf.field.direction") + "###direction-" + shot.shotId(), comboValue,
                Arrays.stream(directions).map(value -> tr("cinewolf.direction."
                        + value.name().toLowerCase(Locale.ROOT))).toArray(String[]::new))) {
            direction[0] = directions[comboValue.get()];
            changed = true;
        }
        tooltip(tr("cinewolf.tooltip.direction"));

        EasingType[] easings = EasingType.values();
        comboValue.set(easing[0].ordinal());
        if (ImGui.combo(tr("cinewolf.field.easing") + "###easing-" + shot.shotId(), comboValue,
                Arrays.stream(easings).map(value -> tr("cinewolf.easing."
                        + value.name().toLowerCase(Locale.ROOT))).toArray(String[]::new))) {
            easing[0] = easings[comboValue.get()];
            changed = true;
        }
        tooltip(tr("cinewolf.tooltip.easing"));
        if (!changed) return;
        ShotRequest replacement = new ShotRequest(request.target(), request.shotType(), values[2], values[0],
                values[1], values[6], values[7], values[8], request.durationSeconds(), values[9], direction[0],
                values[4], values[3], easing[0], values[5],
                request.replayStartTime(), request.replayEndTime());
        updatePlan(planEditor.replaceRequest(plan, shot.shotId(), replacement, shot.framing(),
                List.of("montage.reason.manual_parameters")));
    }

    private void renderPreviewAndGeneration() {
        MontagePlan plan = analysisController.montagePlan();
        if (plan == null) return;
        ImGui.separatorText(tr("cinewolf.montage.section.preview_generate"));
        if (generationController.busy()) {
            ImGui.progressBar(generationController.progress(), -1, 0,
                    localizedStatus(generationController.statusKey(), generationController.statusArguments()));
        } else {
            ImGui.textWrapped(localizedStatus(generationController.statusKey(), generationController.statusArguments()));
        }

        if (ImGui.button(tr("cinewolf.montage.action.preview"))) {
            requestMontagePreview(plan);
        }
        tooltip(tr("cinewolf.montage.tooltip.preview"));
        ImGui.sameLine();
        if (ImGui.button(tr("cinewolf.montage.action.generate"))) {
            requestMontageWrite(plan);
        }
        tooltip(tr("cinewolf.montage.tooltip.generate"));
        ImGui.sameLine();
        if (ImGui.button(tr("cinewolf.montage.action.undo"))) {
            var result = generationController.undoLast();
            showAction(tr(result.success() ? "cinewolf.montage.undo.success"
                            : "cinewolf.montage.undo.unavailable"),
                    result.success() ? NoticeSeverity.SUCCESS : NoticeSeverity.ERROR);
        }
        tooltip(tr("cinewolf.montage.tooltip.undo"));

        generationController.generatedPaths().stream().flatMap(path -> path.path().warnings().stream())
                .filter(warning -> warning.severity() != PathWarning.Severity.ERROR)
                .collect(java.util.stream.Collectors.toMap(warning -> warning.code() + ':' + warning.message(),
                        warning -> warning, (left, right) -> left, java.util.LinkedHashMap::new))
                .values().forEach(warning -> {
                    int color = warning.severity() == PathWarning.Severity.INFO ? 0xFF75D2FF : 0xFFFFB347;
                    ImGui.textColored(color, localizePathWarning(warning));
                });

        if (previewController.active()) {
            ImGui.textColored(75, 210, 255, 255,
                    tr("cinewolf.montage.preview.mode", format(previewController.outputSeconds()),
                            format(plan.outputDurationSeconds())));
            if (ImGui.smallButton(tr("cinewolf.montage.preview.play"))) previewController.play();
            tooltip(tr("cinewolf.montage.tooltip.preview_play"));
            ImGui.sameLine();
            if (ImGui.smallButton(tr("cinewolf.montage.preview.pause"))) previewController.pause();
            tooltip(tr("cinewolf.montage.tooltip.preview_pause"));
            ImGui.sameLine();
            if (ImGui.smallButton(tr("cinewolf.montage.preview.stop"))) previewController.stop();
            tooltip(tr("cinewolf.montage.tooltip.preview_stop"));
            ImGui.sameLine();
            if (ImGui.smallButton(tr("cinewolf.montage.preview.previous"))) previewController.previousShot();
            tooltip(tr("cinewolf.montage.tooltip.preview_previous"));
            ImGui.sameLine();
            if (ImGui.smallButton(tr("cinewolf.montage.preview.next"))) previewController.nextShot();
            tooltip(tr("cinewolf.montage.tooltip.preview_next"));
            ImGui.sameLine();
            if (ImGui.smallButton(tr("cinewolf.montage.preview.exit"))) previewController.exit();
            tooltip(tr("cinewolf.montage.tooltip.preview_exit"));
            ImGui.textDisabled(tr(previewController.statusKey()));
            ImGui.textDisabled(tr("cinewolf.montage.preview.fov_note"));
        }
    }

    private void requestShotPreview(MontagePlan plan, UUID shotId) {
        if (previewController.active()) {
            if (!previewController.seekToShot(shotId)) {
                showAction(tr(previewController.statusKey()), NoticeSeverity.WARNING);
            }
            return;
        }
        requestGenerationAction(plan, PendingMontageAction.Type.PREVIEW_SHOT, shotId);
    }

    private void requestMontagePreview(MontagePlan plan) {
        if (previewController.active()) {
            showAction(tr("cinewolf.montage.preview.already_active"), NoticeSeverity.WARNING);
            return;
        }
        requestGenerationAction(plan, PendingMontageAction.Type.PREVIEW_MONTAGE, null);
    }

    private void requestMontageWrite(MontagePlan plan) {
        requestGenerationAction(plan, PendingMontageAction.Type.WRITE, null);
    }

    private void requestGenerationAction(MontagePlan plan, PendingMontageAction.Type type, UUID shotId) {
        clearPendingAction();
        singleShotPreview.clear();
        if (type == PendingMontageAction.Type.WRITE && previewController.active()) previewController.exit();

        if (generationController.readyFor(plan)) {
            pendingAction = pendingAction(type, plan, generationController.generationId(), shotId);
            return;
        }
        if (generationController.processing(plan)) {
            pendingAction = pendingAction(type, plan, generationController.generationId(), shotId);
            return;
        }
        if (!seekControllersIdle()) {
            showAction(tr("cinewolf.montage.preview.wait_restore"), NoticeSeverity.INFO);
            return;
        }
        if (!generationController.generate(plan, analysisController.analysisResult())) {
            showAction(localizedStatus(generationController.statusKey(), generationController.statusArguments()),
                    NoticeSeverity.ERROR);
            return;
        }
        pendingAction = pendingAction(type, plan, generationController.generationId(), shotId);
    }

    private static PendingMontageAction pendingAction(PendingMontageAction.Type type, MontagePlan plan,
                                                       long generationId, UUID shotId) {
        return switch (type) {
            case PREVIEW_MONTAGE -> PendingMontageAction.preview(plan.montageId(), generationId);
            case PREVIEW_SHOT -> PendingMontageAction.previewShot(plan.montageId(), generationId, shotId);
            case WRITE -> PendingMontageAction.write(plan.montageId(), generationId);
        };
    }

    private void processDeferredActions(TargetReference sharedTarget) {
        if (analysisWhenPreviewRestored && seekControllersIdle()) {
            analysisWhenPreviewRestored = false;
            TargetReference target = sharedTarget;
            analysisController.start(target);
        }
        PendingMontageAction action = pendingAction;
        if (action == null) return;
        MontagePlan plan = analysisController.montagePlan();
        if (plan == null || !action.matches(plan.montageId(), generationController.generationId())) {
            clearPendingAction();
            return;
        }
        if (generationController.state() == MontageGenerationController.State.FAILED
                || generationController.state() == MontageGenerationController.State.IDLE) {
            clearPendingAction();
            return;
        }
        if (!generationController.readyFor(plan, action.generationId()) || !seekControllersIdle()) return;

        clearPendingAction();
        switch (action.type()) {
            case PREVIEW_MONTAGE -> enterPreview(plan);
            case PREVIEW_SHOT -> {
                if (enterPreview(plan) && !previewController.seekToShot(action.shotId())) {
                    showAction(tr(previewController.statusKey()), NoticeSeverity.WARNING);
                }
            }
            case WRITE -> requestWrite();
        }
    }

    private boolean enterPreview(MontagePlan plan) {
        boolean entered = previewController.enter(plan, generationController.generatedPaths());
        if (!entered) showAction(tr(previewController.statusKey()), NoticeSeverity.WARNING);
        return entered;
    }

    private void requestWrite() {
        ReplayEditorAdapter.ReplayTimeRange range = adapter.getSelectedTimeRange();
        if (!range.selected()) {
            showAction(tr("cinewolf.montage.error.select_range"), NoticeSeverity.ERROR);
            return;
        }
        FlashbackMontageTimelineWriter.InspectionResult inspection = generationController.inspect(range.startTick(),
                MontageTimelineWriteOptions.add());
        if (inspection == null || !inspection.valid()) {
            List<ActionNotice> notices = new ArrayList<>();
            if (inspection != null) inspection.warnings().forEach(code -> notices.add(warningNotice(code)));
            String error = inspection == null || inspection.errors().isEmpty()
                    ? tr("cinewolf.montage.error.timeline_write_failed")
                    : localizeCode(inspection.errors().getFirst());
            notices.add(new ActionNotice(error, NoticeSeverity.ERROR));
            showNotices(notices);
            return;
        }
        if (!inspection.warnings().isEmpty()) {
            showNotices(inspection.warnings().stream().map(GenerateMontagePanel::warningNotice).toList());
        }
        if (inspection.conflicts().hasConflicts()) {
            pendingInspection = inspection;
            pendingReplaceInterval = inspection.plan().orElseThrow().outputInterval();
            ImGui.openPopup("CineWolfMontageConflict");
        } else {
            write(range.startTick(), MontageTimelineWriteOptions.cancelOnConflict());
        }
    }

    private void renderConflictDialog() {
        if (!ImGui.beginPopupModal(tr("cinewolf.montage.conflict.title") + "###CineWolfMontageConflict",
                ImGuiWindowFlags.AlwaysAutoResize)) return;
        var conflicts = pendingInspection == null
                ? pl.peterwolf.cinewolf.montage.timeline.MontageTimelineConflictReport.empty()
                : pendingInspection.conflicts();
        ImGui.textWrapped(tr("cinewolf.montage.conflict.summary", conflicts.cameraKeyframes(),
                conflicts.fovKeyframes(), conflicts.timelapseKeyframes(), conflicts.activeSegmentCount()));
        ImGui.textWrapped(tr("cinewolf.montage.conflict.default_cancel"));
        if (ImGui.button(tr("cinewolf.conflict.cancel"))) closeConflict();
        tooltip(tr("cinewolf.tooltip.conflict_cancel"));
        ImGui.sameLine();
        if (ImGui.button(tr("cinewolf.montage.conflict.add"))) {
            write(selectedOutputStart(), MontageTimelineWriteOptions.add());
            closeConflict();
        }
        tooltip(tr("cinewolf.montage.tooltip.conflict_add"));
        if (ImGui.button(tr("cinewolf.montage.conflict.replace")) && pendingReplaceInterval != null) {
            write(selectedOutputStart(), MontageTimelineWriteOptions.replace(pendingReplaceInterval));
            closeConflict();
        }
        tooltip(tr("cinewolf.montage.tooltip.conflict_replace"));
        ImGui.sameLine();
        ImGui.beginDisabled();
        ImGui.button(tr("cinewolf.montage.conflict.after_last"));
        ImGui.endDisabled();
        tooltip(tr("cinewolf.montage.tooltip.conflict_after_last"), ImGuiHoveredFlags.AllowWhenDisabled);
        ImGui.textDisabled(tr("cinewolf.montage.conflict.after_last_unavailable"));
        ImGui.endPopup();
    }

    private void closeConflict() {
        pendingInspection = null;
        pendingReplaceInterval = null;
        ImGui.closeCurrentPopup();
    }

    private void write(long startTick, MontageTimelineWriteOptions options) {
        FlashbackMontageTimelineWriter.WriteResult result = generationController.write(startTick, options);
        if (result == null) {
            showAction(tr("cinewolf.montage.error.timeline_write_failed"), NoticeSeverity.ERROR);
            return;
        }
        List<ActionNotice> notices = new ArrayList<>();
        result.warnings().forEach(code -> notices.add(warningNotice(code)));
        if (result.success()) {
            notices.add(new ActionNotice(tr("cinewolf.montage.write.success", result.cameraKeyframes(),
                    result.fovKeyframes(), result.timelapseKeyframes()), NoticeSeverity.SUCCESS));
        } else {
            String error = result.errors().isEmpty() ? tr("cinewolf.montage.error.timeline_write_failed")
                    : localizeCode(result.errors().getFirst());
            notices.add(new ActionNotice(error, NoticeSeverity.ERROR));
        }
        showNotices(notices);
        if (result.success()) {
            saveProject(config.montage.debugJsonExport);
        }
    }

    private long selectedOutputStart() {
        ReplayEditorAdapter.ReplayTimeRange range = adapter.getSelectedTimeRange();
        return range.selected() ? range.startTick() : Math.max(0L, adapter.getCurrentReplayTime());
    }

    private void saveProject(boolean withDebug) {
        var analysis = analysisController.analysisResult();
        var plan = analysisController.montagePlan();
        if (analysis == null || plan == null) {
            showAction(tr("cinewolf.montage.error.plan_missing"), NoticeSeverity.ERROR);
            return;
        }
        try {
            MontageProject project = MontageProject.capture(adapter.replayIdentifier(), analysis, plan,
                    List.copyOf(manualEdits));
            projectManager.saveProject(project);
            if (withDebug) projectManager.saveDebugExport(MontageDebugExport.capture(project, analysis, plan));
            showAction(tr(withDebug ? "cinewolf.montage.project.debug_saved" : "cinewolf.montage.project.saved"),
                    NoticeSeverity.SUCCESS);
        } catch (IOException | RuntimeException exception) {
            logger.warn("Unable to save CineWolf montage project", exception);
            showAction(tr("cinewolf.montage.project.save_failed"), NoticeSeverity.ERROR);
        }
    }

    private void updatePlan(MontagePlan replacement) {
        MontagePlan previous = analysisController.montagePlan();
        if (previous != null && replacement != null && !previous.equals(replacement)) {
            recordManualEdits(previous, replacement);
        }
        clearPendingAction();
        previewController.exit();
        generationController.clear();
        analysisController.setPlan(replacement);
    }

    private void settingsChanged() {
        manualEdits.clear();
        clearPendingAction();
        boolean hadAnalysis = analysisController.currentRequest() != null;
        config.montage.normalize();
        configManager.save();
        previewController.exit();
        generationController.clear();
        analysisController.clear();
        selectedEventId = null;
        if (hadAnalysis) {
            showAction(tr("cinewolf.montage.settings.reanalyze_or_replan"), NoticeSeverity.WARNING);
        }
    }

    void pathSettingsChanged() {
        clearPendingAction();
        configManager.save();
        previewController.exit();
        generationController.clear();
        singleShotPreview.clear();
    }

    private void recordManualEdits(MontagePlan previous, MontagePlan replacement) {
        java.util.Map<UUID, PlannedMontageShot> before = previous.shots().stream().collect(
                java.util.stream.Collectors.toMap(PlannedMontageShot::shotId, shot -> shot));
        java.util.Map<UUID, PlannedMontageShot> after = replacement.shots().stream().collect(
                java.util.stream.Collectors.toMap(PlannedMontageShot::shotId, shot -> shot));
        long editedAt = System.currentTimeMillis();
        for (PlannedMontageShot oldShot : previous.shots()) {
            PlannedMontageShot newShot = after.get(oldShot.shotId());
            if (newShot == null) {
                addManualEdit(oldShot.shotId(), "shot", "present", "removed", editedAt);
                continue;
            }
            recordIfChanged(oldShot.shotId(), "enabled", oldShot.enabled(), newShot.enabled(), editedAt);
            recordIfChanged(oldShot.shotId(), "locked", oldShot.locked(), newShot.locked(), editedAt);
            recordIfChanged(oldShot.shotId(), "order", oldShot.order(), newShot.order(), editedAt);
            recordIfChanged(oldShot.shotId(), "shotType", oldShot.shotType(), newShot.shotType(), editedAt);
            recordIfChanged(oldShot.shotId(), "target", oldShot.target().uuid(), newShot.target().uuid(), editedAt);
            recordIfChanged(oldShot.shotId(), "framing", oldShot.framing(), newShot.framing(), editedAt);
            recordIfChanged(oldShot.shotId(), "sourceRange",
                    oldShot.sourceReplayStartTime() + ".." + oldShot.sourceReplayEndTime(),
                    newShot.sourceReplayStartTime() + ".." + newShot.sourceReplayEndTime(), editedAt);
            recordIfChanged(oldShot.shotId(), "output",
                    oldShot.outputStartSeconds() + "+" + oldShot.outputDurationSeconds(),
                    newShot.outputStartSeconds() + "+" + newShot.outputDurationSeconds(), editedAt);
            recordIfChanged(oldShot.shotId(), "parameters", oldShot.shotRequest(), newShot.shotRequest(), editedAt);
        }
        for (PlannedMontageShot newShot : replacement.shots()) {
            if (!before.containsKey(newShot.shotId())) {
                addManualEdit(newShot.shotId(), "shot", "missing",
                        "created:" + newShot.shotType(), editedAt);
            }
        }
    }

    private void recordIfChanged(UUID shotId, String field, Object previous, Object replacement, long editedAt) {
        if (!java.util.Objects.equals(previous, replacement)) {
            addManualEdit(shotId, field, String.valueOf(previous), String.valueOf(replacement), editedAt);
        }
    }

    private void addManualEdit(UUID shotId, String field, String previous, String replacement, long editedAt) {
        if (manualEdits.size() >= 1_024) manualEdits.removeFirst();
        manualEdits.add(new MontageProject.ManualEditSummary(shotId, field, previous, replacement, editedAt));
    }

    private void updateSafeAreaOverlay() {
        if (previewController.active() && config.montage.aspectRatio == OutputAspectRatio.VERTICAL_9_16) {
            safeAreaOverlay.show(config.montage.verticalSafeArea);
        } else safeAreaOverlay.hide();
    }

    private void renderMessages() {
        if (System.currentTimeMillis() < actionNoticesUntil) {
            for (ActionNotice notice : actionNotices) {
                ImGui.textColored(notice.severity().color(), notice.message());
            }
        }
    }

    public boolean prepareForSingleShotSeek() {
        clearPendingAction();
        singleShotPreview.clear();
        if (analysisController.busy()) analysisController.cancel();
        generationController.cancel(true);
        analysisWhenPreviewRestored = false;
        if (previewController.active()) {
            previewController.exit();
        }
        safeAreaOverlay.hide();
        return seekControllersIdle();
    }

    private void showAction(String message, NoticeSeverity severity) {
        List<ActionNotice> notices = new ArrayList<>();
        if (System.currentTimeMillis() < actionNoticesUntil) notices.addAll(actionNotices);
        notices.add(new ActionNotice(message, severity));
        showNotices(notices);
    }

    private void showNotices(List<ActionNotice> notices) {
        actionNotices = List.copyOf(notices);
        actionNoticesUntil = System.currentTimeMillis() + 7000L;
    }

    private static ActionNotice warningNotice(String code) {
        return new ActionNotice(localizeCode(code), NoticeSeverity.WARNING);
    }

    private void clearPendingAction() {
        pendingAction = null;
        pendingInspection = null;
        pendingReplaceInterval = null;
    }

    private boolean seekControllersIdle() {
        return !singleShotPreview.busy() && !analysisController.busy() && !generationController.busy()
                && !previewController.active();
    }

    private boolean toggle(String key, boolean current, String tooltipKey,
                           java.util.function.Consumer<Boolean> update) {
        boolean changed = ImGui.checkbox(tr(key), current);
        tooltip(tr(tooltipKey));
        if (!changed) return false;
        update.accept(!current);
        return true;
    }

    private boolean number(String label, double current, double step, double minimum, double maximum,
                           java.util.function.DoubleConsumer update) {
        return number(label, current, step, minimum, maximum, null, ImGuiHoveredFlags.None, update);
    }

    private boolean number(String label, double current, double step, double minimum, double maximum,
                           String tooltip, java.util.function.DoubleConsumer update) {
        return number(label, current, step, minimum, maximum, tooltip, ImGuiHoveredFlags.None, update);
    }

    private boolean number(String label, double current, double step, double minimum, double maximum,
                           String tooltip, int tooltipFlags, java.util.function.DoubleConsumer update) {
        floatValue.set((float) current);
        boolean changed = ImGui.inputFloat(label, floatValue, (float) step, (float) (step * 10.0), "%.3f");
        if (tooltip != null) tooltip(tooltip, tooltipFlags);
        if (!changed) return false;
        update.accept(Math.max(minimum, Math.min(maximum, floatValue.get())));
        return true;
    }

    private static void tooltip(String text) {
        tooltip(text, ImGuiHoveredFlags.None);
    }

    private static void tooltip(String text, int flags) {
        if (ImGui.isItemHovered(TOOLTIP_HOVER_FLAGS | flags)) {
            ImGui.beginTooltip();
            ImGui.pushTextWrapPos(ImGui.getFontSize() * 32.0f);
            ImGui.textUnformatted(text);
            ImGui.popTextWrapPos();
            ImGui.endTooltip();
        }
    }

    private MontagePreset preset(MontagePresetType type) {
        return presets.get(type).orElseThrow();
    }

    private static List<TargetReference> availableTargets(List<ReplayEditorAdapter.ReplayEntityDescriptor> entities,
                                                           TargetReference shared, TargetReference current) {
        List<TargetReference> result = new ArrayList<>();
        if (current != null) result.add(current);
        if (shared != null && !result.contains(shared)) result.add(shared);
        for (ReplayEditorAdapter.ReplayEntityDescriptor entity : entities) {
            if (!result.contains(entity.reference())) result.add(entity.reference());
        }
        return List.copyOf(result);
    }

    private static ShotRequest copyRequest(ShotRequest source, TargetReference target, ShotType type) {
        RotationDirection direction = source.direction();
        if (type == ShotType.FLYBY && direction != RotationDirection.LEFT_TO_RIGHT
                && direction != RotationDirection.RIGHT_TO_LEFT) direction = RotationDirection.LEFT_TO_RIGHT;
        if (type != ShotType.FLYBY && direction != RotationDirection.CLOCKWISE
                && direction != RotationDirection.COUNTERCLOCKWISE) direction = RotationDirection.CLOCKWISE;
        return new ShotRequest(target, type, source.diameter(), source.height(), source.distance(),
                source.startDistance(), source.endDistance(), source.rpm(), source.durationSeconds(),
                source.startAngleDegrees(), direction, source.cameraSpeed(), source.fov(), source.easing(),
                source.lookAheadSeconds(), source.replayStartTime(), source.replayEndTime());
    }

    private static String localizedStatus(String key, List<String> arguments) {
        return tr(key, arguments.stream().map(GenerateMontagePanel::localizeArgument).toArray());
    }

    private static String localizeCode(String key) {
        return localizeCode(key, List.of());
    }

    private static String localizeCode(String key, List<String> arguments) {
        if (key.startsWith("montage.reason.event.")) {
            String eventKey = "cinewolf.event." + key.substring("montage.reason.event.".length());
            return tr("cinewolf.montage.reason.detected_event", tr(eventKey));
        }
        if (key.startsWith("montage.reason.shot_fallback;")) {
            java.util.Map<String, String> values = encodedValues(key);
            return tr("montage.reason.shot_fallback", localizeArgument(values.get("requested")),
                    localizeArgument(values.get("chosen")));
        }
        String scoreReason = localizeScoreReason(key);
        if (scoreReason != null) return scoreReason;
        String translated = tr(key, arguments.toArray());
        if (!translated.equals(key)) return translated;
        int lastDot = key.lastIndexOf('.');
        return key.substring(lastDot + 1).replace('_', ' ');
    }

    private static String localizeScoreReason(String encoded) {
        int equals = encoded.indexOf('=');
        if (equals <= 0) return null;
        java.util.Map<String, String> values = encodedValues(encoded);
        String name = encoded.substring(0, equals);
        return switch (name) {
            case "importance" -> tr("cinewolf.score.importance", values.get("importance"), values.get("basis"));
            case "cinematic" -> tr("cinewolf.score.cinematic", values.get("cinematic"), values.get("basis"));
            case "uniqueness" -> tr("cinewolf.score.uniqueness", values.get("uniqueness"),
                    values.get("occurrences"));
            case "preset_compatibility" -> tr("cinewolf.score.preset_compatibility",
                    values.get("preset_compatibility"));
            case "marker_bonus" -> tr("cinewolf.score.marker_bonus", values.get("marker_bonus"));
            case "selected_target_bonus" -> tr("cinewolf.score.selected_target_bonus",
                    values.get("selected_target_bonus"));
            case "repetition_penalty" -> tr("cinewolf.score.repetition_penalty",
                    values.get("repetition_penalty"));
            case "technical_risk_penalty" -> tr("cinewolf.score.technical_risk_penalty",
                    values.get("technical_risk_penalty"), values.get("confidence"));
            default -> null;
        };
    }

    private static java.util.Map<String, String> encodedValues(String encoded) {
        java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
        for (String part : encoded.split(";")) {
            int separator = part.indexOf('=');
            if (separator > 0) values.put(part.substring(0, separator), part.substring(separator + 1));
        }
        return values;
    }

    private static String localizeArgument(String argument) {
        if (argument == null) return "";
        String translated = tr(argument);
        return translated.equals(argument) ? argument : translated;
    }

    private static String localizeEvidenceToken(String category, String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.toLowerCase(Locale.ROOT).replace(' ', '_');
        if (!normalized.matches("[a-z0-9_]+")) return value;
        String key = "cinewolf.montage.event." + category + "." + normalized;
        String translated = tr(key);
        return translated.equals(key) ? value : translated;
    }

    private static String localizePathWarning(PathWarning warning) {
        String key = "cinewolf.path_warning." + warning.code();
        String argument = warning.message().replaceAll("\\D+", " ").trim().split(" ")[0];
        String translated = argument.isBlank() ? tr(key) : tr(key, argument);
        return translated.equals(key) ? tr("cinewolf.montage.warning.path_review") : translated;
    }

    private static String timestamp(long tick) {
        long millis = Math.max(0L, tick) * 50L;
        return String.format(Locale.ROOT, "%02d:%02d.%03d", millis / 60_000L,
                (millis / 1000L) % 60L, millis % 1000L);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String tr(String key, Object... arguments) {
        return I18n.get(key, arguments);
    }

    private record ActionNotice(String message, NoticeSeverity severity) {
    }

    private enum NoticeSeverity {
        INFO(0xFF75D2FF),
        SUCCESS(0xFF7DFF9B),
        WARNING(0xFFFFB347),
        ERROR(0xFFFF5555);

        private final int color;

        NoticeSeverity(int color) {
            this.color = color;
        }

        private int color() {
            return color;
        }
    }
}
