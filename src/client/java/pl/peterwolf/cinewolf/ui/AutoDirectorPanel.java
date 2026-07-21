package pl.peterwolf.cinewolf.ui;

import com.moulberry.flashback.editor.ui.ReplayUI;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiHoveredFlags;
import imgui.moulberry90.flag.ImGuiWindowFlags;
import imgui.moulberry90.type.ImFloat;
import imgui.moulberry90.type.ImInt;
import net.minecraft.client.resources.language.I18n;
import pl.peterwolf.cinewolf.api.ReplayEditorAdapter;
import pl.peterwolf.cinewolf.camera.ReplayIntervalResolver;
import pl.peterwolf.cinewolf.config.CineWolfConfig;
import pl.peterwolf.cinewolf.config.CineWolfConfigManager;
import pl.peterwolf.cinewolf.integration.flashback.FlashbackReplayEditorAdapter;
import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.EasingType;
import pl.peterwolf.cinewolf.model.PathWarning;
import pl.peterwolf.cinewolf.model.RotationDirection;
import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.preview.PreviewController;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class AutoDirectorPanel {
    private static final long AUTO_PREVIEW_DEBOUNCE_MILLIS = 350L;
    private static final int TOOLTIP_HOVER_FLAGS = ImGuiHoveredFlags.ForTooltip
            | ImGuiHoveredFlags.Stationary | ImGuiHoveredFlags.DelayNormal | ImGuiHoveredFlags.NoSharedDelay;
    private final FlashbackReplayEditorAdapter adapter;
    private final PreviewController previewController;
    private final CineWolfConfigManager configManager;
    private final CineWolfConfig config;
    private final GenerateMontagePanel montagePanel;
    private final ReplayIntervalResolver intervalResolver = new ReplayIntervalResolver();
    private final ImFloat floatValue = new ImFloat();
    private final ImInt comboValue = new ImInt();
    private TargetReference target;
    private List<ReplayEditorAdapter.ReplayEntityDescriptor> entities = List.of();
    private long entityListTick = Long.MIN_VALUE;
    private long lastEntityRefresh;
    private long lastChangeMillis;
    private boolean savePending;
    private boolean autoRegenerate;
    private String actionMessage;
    private long actionMessageUntil;
    private CameraPathPlan pendingConflictPlan;

    public AutoDirectorPanel(FlashbackReplayEditorAdapter adapter, PreviewController previewController,
                             CineWolfConfigManager configManager, GenerateMontagePanel montagePanel) {
        this.adapter = adapter;
        this.previewController = previewController;
        this.configManager = configManager;
        this.config = configManager.get();
        this.montagePanel = montagePanel;
    }

    public void render() {
        if (!adapter.isReplayEditorOpen()) return;
        refreshEntitiesIfNeeded();
        handleDeferredActions();

        ImGui.setNextWindowSizeConstraints(360, 180, 760, 1100);
        if (ImGui.begin(tr("cinewolf.panel.title") + "###CineWolfAutoDirector", ImGuiWindowFlags.AlwaysAutoResize)) {
            renderTargetSelector();
            if (ImGui.beginTabBar("CineWolfModes")) {
                if (ImGui.beginTabItem(tr("cinewolf.tab.single_shot"))) {
                    renderShotSelector();
                    ImGui.separator();
                    renderShotFields();
                    renderTimelineSummary();
                    renderPreviewControls();
                    renderConflictDialog();
                    ImGui.endTabItem();
                }
                if (ImGui.beginTabItem(tr("cinewolf.tab.generate_montage"))) {
                    montagePanel.render(target, entities);
                    ImGui.endTabItem();
                }
                ImGui.endTabBar();
            }
        }
        ImGui.end();
    }

    private void renderTargetSelector() {
        String preview = target == null ? tr("cinewolf.target.none")
                : target.displayName() + " • " + target.entityType() + " • " + target.shortIdentifier();
        if (ImGui.beginCombo(tr("cinewolf.field.target"), preview)) {
            if (ImGui.selectable(tr("cinewolf.target.none"), target == null)) {
                target = null;
                markChanged();
            }
            for (ReplayEditorAdapter.ReplayEntityDescriptor entity : entities) {
                boolean selected = target != null && target.uuid().equals(entity.reference().uuid());
                String availability = entity.available() ? "●" : "○";
                String label = availability + " " + entity.name() + " • " + entity.entityType() + " • " + entity.shortIdentifier();
                if (ImGui.selectable(label, selected)) {
                    target = entity.reference();
                    markChanged();
                }
                if (selected) ImGui.setItemDefaultFocus();
            }
            ImGui.endCombo();
        }
        tooltip(tr("cinewolf.tooltip.target"));

        if (ImGui.smallButton(tr("cinewolf.target.flashback_selected"))) {
            selectTarget(adapter.targetSelectedInFlashback(), tr("cinewolf.error.no_flashback_selection"));
        }
        tooltip(tr("cinewolf.tooltip.target_flashback_selected"));
        ImGui.sameLine();
        if (ImGui.smallButton(tr("cinewolf.target.crosshair"))) {
            selectTarget(adapter.targetUnderCrosshair(), tr("cinewolf.error.no_crosshair_target"));
        }
        tooltip(tr("cinewolf.tooltip.target_crosshair"));
        ImGui.sameLine();
        if (ImGui.smallButton(tr("cinewolf.target.spectated"))) {
            selectTarget(adapter.spectatedTarget(), tr("cinewolf.error.no_spectated_target"));
        }
        tooltip(tr("cinewolf.tooltip.target_spectated"));

        if (target != null) {
            boolean available = entities.stream().anyMatch(entity -> entity.available() && entity.reference().uuid().equals(target.uuid()));
            if (!available) ImGui.textColored(255, 170, 40, 255, tr("cinewolf.target.unavailable"));
        }
    }

    private void renderShotSelector() {
        ShotType oldShot = config.shotType;
        comboValue.set(config.shotType.ordinal());
        if (ImGui.combo(tr("cinewolf.field.shot_type"), comboValue,
                java.util.Arrays.stream(ShotType.values()).map(value -> tr("cinewolf.shot."
                        + value.name().toLowerCase(java.util.Locale.ROOT))).toArray(String[]::new))) {
            config.shotType = ShotType.values()[comboValue.get()];
            if (oldShot != config.shotType) config.resetFor(config.shotType);
            markChanged();
        }
        tooltip(tr("cinewolf.tooltip.shot_type"));
    }

    private void renderShotFields() {
        boolean changed = false;
        if (config.shotType == ShotType.ORBIT) {
            changed |= number(tr("cinewolf.field.diameter"), config.diameter, 0.5, 0.1, 256.0,
                    tr("cinewolf.tooltip.diameter"), value -> config.diameter = value);
        }

        changed |= number(tr("cinewolf.field.height"), config.height, 0.25, -64.0, 256.0,
                tr("cinewolf.tooltip.height"), value -> config.height = value);

        if (config.shotType == ShotType.FOLLOW || config.shotType == ShotType.FLYBY) {
            changed |= number(tr("cinewolf.field.distance"), config.distance, 0.5, 0.25, 512.0,
                    tr("cinewolf.tooltip.distance"), value -> config.distance = value);
        }
        if (config.shotType == ShotType.DOLLY_IN || config.shotType == ShotType.DOLLY_OUT) {
            changed |= number(tr("cinewolf.field.start_distance"), config.startDistance, 0.5, 1.0, 512.0,
                    tr("cinewolf.tooltip.start_distance"), value -> config.startDistance = value);
            changed |= number(tr("cinewolf.field.end_distance"), config.endDistance, 0.5, 1.0, 512.0,
                    tr("cinewolf.tooltip.end_distance"), value -> config.endDistance = value);
        }
        if (config.shotType == ShotType.ORBIT) {
            changed |= number(tr("cinewolf.field.rpm"), config.rpm, 0.05, 0.0, 60.0,
                    tr("cinewolf.tooltip.rpm"), value -> config.rpm = value);
            changed |= number(tr("cinewolf.field.start_angle"), config.startAngleDegrees, 5.0, -3600.0, 3600.0,
                    tr("cinewolf.tooltip.start_angle"), value -> config.startAngleDegrees = value);
            changed |= directionCombo(new RotationDirection[]{RotationDirection.CLOCKWISE, RotationDirection.COUNTERCLOCKWISE});
            ImGui.textDisabled(tr("cinewolf.stats.revolutions", format(config.rpm * effectiveDurationSeconds() / 60.0)));
        } else if (config.shotType == ShotType.FLYBY) {
            changed |= directionCombo(new RotationDirection[]{RotationDirection.LEFT_TO_RIGHT, RotationDirection.RIGHT_TO_LEFT});
        }

        if (config.shotType == ShotType.FOLLOW || config.shotType == ShotType.FLYBY) {
            changed |= number(tr("cinewolf.field.camera_speed"), config.cameraSpeed, 0.25, 0.05, 128.0,
                    tr("cinewolf.tooltip.camera_speed"), value -> config.cameraSpeed = value);
        }

        ReplayEditorAdapter.ReplayTimeRange range = adapter.getSelectedTimeRange();
        if (range.selected()) {
            ImGui.beginDisabled();
            floatValue.set((float) ((range.endTick() - range.startTick()) / 20.0));
            ImGui.inputFloat(tr("cinewolf.field.duration"), floatValue, 0.0f, 0.0f, "%.2f s");
            ImGui.endDisabled();
            tooltip(tr("cinewolf.tooltip.duration_range"), ImGuiHoveredFlags.AllowWhenDisabled);
        } else {
            changed |= number(tr("cinewolf.field.duration"), config.durationSeconds, 0.5, 0.05, 3600.0,
                    tr("cinewolf.tooltip.duration"), value -> config.durationSeconds = value);
        }

        changed |= number(tr("cinewolf.field.fov"), config.fov, 1.0, 1.0, 110.0,
                tr("cinewolf.tooltip.fov"), value -> config.fov = value);
        changed |= easingCombo();

        if (config.shotType == ShotType.ORBIT || config.shotType == ShotType.FOLLOW
                || config.shotType == ShotType.DOLLY_IN || config.shotType == ShotType.DOLLY_OUT) {
            changed |= number(tr("cinewolf.field.look_ahead"), config.lookAheadSeconds, 0.05, 0.0, 10.0,
                    tr("cinewolf.tooltip.look_ahead"), value -> config.lookAheadSeconds = value);
        }

        renderPathSmoothingFields();

        ImGui.beginDisabled();
        ImGui.checkbox(tr("cinewolf.field.collision_avoidance"), false);
        ImGui.endDisabled();
        tooltip(tr("cinewolf.tooltip.collision_avoidance"), ImGuiHoveredFlags.AllowWhenDisabled);

        if (changed) markChanged();
        if (ImGui.button(tr("cinewolf.action.reset_defaults"))) {
            config.resetFor(config.shotType);
            markChanged();
        }
        tooltip(tr("cinewolf.tooltip.reset_defaults"));
    }

    private void renderPathSmoothingFields() {
        ImGui.separatorText(tr("cinewolf.section.path_smoothing"));
        boolean changed = false;

        boolean enabled = config.pathSmoothing.enabled;
        if (ImGui.checkbox(tr("cinewolf.field.path_smoothing_enabled"), enabled)) {
            config.pathSmoothing.enabled = !enabled;
            changed = true;
        }
        tooltip(tr("cinewolf.tooltip.path_smoothing_enabled"));

        boolean smoothingDisabled = !config.pathSmoothing.enabled;
        int smoothingTooltipFlags = smoothingDisabled ? ImGuiHoveredFlags.AllowWhenDisabled : ImGuiHoveredFlags.None;
        if (smoothingDisabled) ImGui.beginDisabled();
        changed |= number(tr("cinewolf.field.position_smoothing_strength"),
                config.pathSmoothing.positionStrength, 0.05, 0.0, 1.0,
                tr("cinewolf.tooltip.position_smoothing_strength"), smoothingTooltipFlags,
                value -> config.pathSmoothing.positionStrength = value);
        changed |= number(tr("cinewolf.field.rotation_smoothing_strength"),
                config.pathSmoothing.rotationStrength, 0.05, 0.0, 1.0,
                tr("cinewolf.tooltip.rotation_smoothing_strength"), smoothingTooltipFlags,
                value -> config.pathSmoothing.rotationStrength = value);
        changed |= number(tr("cinewolf.field.smoothing_window"), config.pathSmoothing.windowSeconds,
                0.05, 0.05, 2.0, tr("cinewolf.tooltip.smoothing_window"), smoothingTooltipFlags,
                value -> config.pathSmoothing.windowSeconds = value);

        boolean rejectOutliers = config.pathSmoothing.outlierRejection;
        if (ImGui.checkbox(tr("cinewolf.field.outlier_rejection"), rejectOutliers)) {
            config.pathSmoothing.outlierRejection = !rejectOutliers;
            changed = true;
        }
        tooltip(tr("cinewolf.tooltip.outlier_rejection"), smoothingTooltipFlags);
        if (smoothingDisabled) ImGui.endDisabled();

        boolean outlierFieldsDisabled = smoothingDisabled || !config.pathSmoothing.outlierRejection;
        int outlierTooltipFlags = outlierFieldsDisabled
                ? ImGuiHoveredFlags.AllowWhenDisabled : ImGuiHoveredFlags.None;
        if (outlierFieldsDisabled) ImGui.beginDisabled();
        changed |= number(tr("cinewolf.field.outlier_distance"), config.pathSmoothing.outlierThresholdBlocks,
                0.25, 0.25, 64.0, tr("cinewolf.tooltip.outlier_distance"), outlierTooltipFlags,
                value -> config.pathSmoothing.outlierThresholdBlocks = value);
        changed |= number(tr("cinewolf.field.outlier_speed"),
                config.pathSmoothing.outlierSpeedThresholdBlocksPerSecond,
                1.0, 1.0, 512.0, tr("cinewolf.tooltip.outlier_speed"), outlierTooltipFlags,
                value -> config.pathSmoothing.outlierSpeedThresholdBlocksPerSecond = value);
        if (outlierFieldsDisabled) ImGui.endDisabled();

        if (changed) {
            markChanged();
            montagePanel.pathSettingsChanged();
        }
    }

    private void renderTimelineSummary() {
        ImGui.separatorText(tr("cinewolf.section.timeline"));
        ReplayIntervalResolver.Resolution interval = resolveInterval();
        ShotRequest request = buildRequest();
        ImGui.textUnformatted(tr("cinewolf.timeline.start", timestamp(request.replayStartTime()), request.replayStartTime()));
        ImGui.textUnformatted(tr("cinewolf.timeline.end", timestamp(request.replayEndTime()), request.replayEndTime()));
        ImGui.textUnformatted(tr("cinewolf.timeline.duration", format(request.durationSeconds())));
        int estimate = Math.max(2, (int) Math.ceil(request.durationSeconds() * config.samplesPerSecond) + 1);
        ImGui.textUnformatted(tr("cinewolf.timeline.estimated", Math.min(estimate, config.maximumKeyframes)));
        if (adapter.getSelectedTimeRange().selected()) ImGui.textDisabled(tr("cinewolf.timeline.using_range"));
        else ImGui.textDisabled(tr("cinewolf.timeline.using_duration"));
        if (interval.clippedToReplayEnd()) {
            ImGui.textColored(0xFFFFB347, tr("cinewolf.timeline.clamped_to_end"));
        }
    }

    private void renderPreviewControls() {
        ImGui.separatorText(tr("cinewolf.section.preview"));
        String previewStatus = tr(previewController.statusKey(), previewController.statusArguments().toArray());
        if (previewController.busy()) ImGui.progressBar(previewController.progress(), -1, 0, previewStatus);
        else ImGui.textWrapped(previewStatus);

        CameraPathPlan plan = previewController.currentPlan();
        if (plan != null) {
            for (PathWarning warning : plan.warnings()) {
                int color = warning.severity() == PathWarning.Severity.ERROR ? 0xFFFF5555 : 0xFFFFB347;
                ImGui.textColored(color, localizePathWarning(warning));
            }
        }
        if (actionMessage != null && System.currentTimeMillis() < actionMessageUntil) {
            ImGui.textColored(0xFF7DFF9B, actionMessage);
        }

        if (ImGui.button(tr("cinewolf.action.preview_path"))) requestPreview();
        tooltip(tr("cinewolf.tooltip.preview_path"));
        ImGui.sameLine();
        if (ImGui.button(tr("cinewolf.action.clear_preview"))) previewController.clear();
        tooltip(tr("cinewolf.tooltip.clear_preview"));

        boolean visible = config.previewVisible;
        if (ImGui.checkbox(tr("cinewolf.action.preview_visible"), visible)) {
            config.previewVisible = !visible;
            pl.peterwolf.cinewolf.CineWolfAutoDirectorClient.setPreviewVisible(config.previewVisible);
            markSavePending();
        }
        tooltip(tr("cinewolf.tooltip.preview_visible"));

        ShotRequest current = buildRequest();
        boolean canGenerate = previewController.canGenerate(current);
        if (!canGenerate) ImGui.beginDisabled();
        if (ImGui.button(tr("cinewolf.action.generate_shot")) && canGenerate) beginWrite();
        if (!canGenerate) ImGui.endDisabled();
        tooltip(canGenerate ? tr("cinewolf.tooltip.generate") : tr("cinewolf.tooltip.generate_stale"),
                canGenerate ? ImGuiHoveredFlags.None : ImGuiHoveredFlags.AllowWhenDisabled);

        ImGui.sameLine();
        if (ImGui.button(tr("cinewolf.action.undo"))) {
            ReplayEditorAdapter.UndoResult result = adapter.undoLastCineWolfOperation();
            showAction(tr(result.message()));
        }
        tooltip(tr("cinewolf.tooltip.undo"));
    }

    private void beginWrite() {
        CameraPathPlan plan = previewController.currentPlan();
        if (plan == null) return;
        ReplayEditorAdapter.KeyframeConflictReport conflicts = adapter.detectConflicts(plan);
        if (conflicts.hasConflicts()) {
            pendingConflictPlan = plan;
            ImGui.openPopup("CineWolfConflict");
        } else {
            write(plan, ReplayEditorAdapter.ConflictMode.ADD_WITHOUT_DELETING);
        }
    }

    private void renderConflictDialog() {
        if (ImGui.beginPopupModal("CineWolfConflict", ImGuiWindowFlags.AlwaysAutoResize)) {
            ReplayEditorAdapter.KeyframeConflictReport conflicts = pendingConflictPlan == null
                    ? new ReplayEditorAdapter.KeyframeConflictReport(0, 0) : adapter.detectConflicts(pendingConflictPlan);
            ImGui.textWrapped(tr("cinewolf.conflict.summary", conflicts.cameraKeyframes(), conflicts.fovKeyframes()));
            ImGui.textWrapped(tr("cinewolf.conflict.default_cancel"));
            if (ImGui.button(tr("cinewolf.conflict.cancel"))) {
                pendingConflictPlan = null;
                ImGui.closeCurrentPopup();
            }
            tooltip(tr("cinewolf.tooltip.conflict_cancel"));
            ImGui.sameLine();
            if (ImGui.button(tr("cinewolf.conflict.add")) && pendingConflictPlan != null) {
                write(pendingConflictPlan, ReplayEditorAdapter.ConflictMode.ADD_WITHOUT_DELETING);
                pendingConflictPlan = null;
                ImGui.closeCurrentPopup();
            }
            tooltip(tr("cinewolf.tooltip.conflict_add"));
            if (ImGui.button(tr("cinewolf.conflict.replace")) && pendingConflictPlan != null) {
                write(pendingConflictPlan, ReplayEditorAdapter.ConflictMode.REPLACE_INSIDE_INTERVAL);
                pendingConflictPlan = null;
                ImGui.closeCurrentPopup();
            }
            tooltip(tr("cinewolf.tooltip.conflict_replace"));
            ImGui.endPopup();
        }
    }

    private void write(CameraPathPlan plan, ReplayEditorAdapter.ConflictMode mode) {
        ReplayEditorAdapter.KeyframeWriteResult result = adapter.writeCameraPath(plan,
                new ReplayEditorAdapter.KeyframeWriteOptions(mode));
        showAction(tr(result.message(), result.cameraKeyframes(), result.fovKeyframes()));
        if (result.success()) previewController.clear();
    }

    private void requestPreview() {
        if (!montagePanel.prepareForSingleShotSeek()) {
            showAction(tr("cinewolf.montage.preview.wait_restore"));
            return;
        }
        ShotRequest request = buildRequest();
        String error = preliminaryError(request);
        if (error != null) {
            showAction(error);
            return;
        }
        previewController.requestPreview(request, config.samplingSettings());
    }

    private String preliminaryError(ShotRequest request) {
        if (target == null) return tr("cinewolf.error.select_target");
        if (!Double.isFinite(request.durationSeconds()) || request.durationSeconds() <= 0.0) return tr("cinewolf.error.duration");
        if (request.replayEndTime() <= request.replayStartTime()) return tr("cinewolf.error.timeline_backwards");
        long totalReplayTime = adapter.getTotalReplayTime();
        if (totalReplayTime < 0 || request.replayStartTime() < 0 || request.replayEndTime() > totalReplayTime) {
            return tr("cinewolf.error.timeline_unavailable");
        }
        int samples = (int) Math.ceil(request.durationSeconds() * config.samplesPerSecond) + 1;
        if (samples > config.maximumSamples) return tr("cinewolf.error.sample_limit", config.maximumSamples);
        return null;
    }

    private ShotRequest buildRequest() {
        ReplayIntervalResolver.Resolution interval = resolveInterval();
        long start = interval.startTick();
        long end = interval.endTick();
        double duration = interval.durationSeconds();
        RotationDirection direction = config.direction;
        if (config.shotType == ShotType.ORBIT && direction != RotationDirection.CLOCKWISE
                && direction != RotationDirection.COUNTERCLOCKWISE) direction = RotationDirection.CLOCKWISE;
        if (config.shotType == ShotType.FLYBY && direction != RotationDirection.LEFT_TO_RIGHT
                && direction != RotationDirection.RIGHT_TO_LEFT) direction = RotationDirection.LEFT_TO_RIGHT;
        return new ShotRequest(target == null ? new TargetReference(new java.util.UUID(0L, 0L), "unknown", "none") : target,
                config.shotType, config.diameter, config.height, config.distance, config.startDistance, config.endDistance,
                config.rpm, duration, config.startAngleDegrees, direction, config.cameraSpeed, config.fov, config.easing,
                config.lookAheadSeconds, start, end);
    }

    private boolean directionCombo(RotationDirection[] values) {
        int selected = 0;
        for (int i = 0; i < values.length; i++) if (values[i] == config.direction) selected = i;
        comboValue.set(selected);
        if (ImGui.combo(tr("cinewolf.field.direction"), comboValue,
                java.util.Arrays.stream(values).map(value -> tr("cinewolf.direction."
                        + value.name().toLowerCase(java.util.Locale.ROOT))).toArray(String[]::new))) {
            config.direction = values[comboValue.get()];
            tooltip(tr("cinewolf.tooltip.direction"));
            return true;
        }
        tooltip(tr("cinewolf.tooltip.direction"));
        return false;
    }

    private boolean easingCombo() {
        comboValue.set(config.easing.ordinal());
        if (ImGui.combo(tr("cinewolf.field.easing"), comboValue,
                java.util.Arrays.stream(EasingType.values()).map(value -> tr("cinewolf.easing."
                        + value.name().toLowerCase(java.util.Locale.ROOT))).toArray(String[]::new))) {
            config.easing = EasingType.values()[comboValue.get()];
            tooltip(tr("cinewolf.tooltip.easing"));
            return true;
        }
        tooltip(tr("cinewolf.tooltip.easing"));
        return false;
    }

    private boolean number(String label, double current, double step, double minimum, double maximum,
                           String tooltip, java.util.function.DoubleConsumer update) {
        return number(label, current, step, minimum, maximum, tooltip, ImGuiHoveredFlags.None, update);
    }

    private boolean number(String label, double current, double step, double minimum, double maximum,
                           String tooltip, int tooltipFlags, java.util.function.DoubleConsumer update) {
        floatValue.set((float) current);
        boolean changed = ImGui.inputFloat(label, floatValue, (float) step, (float) (step * 10.0), "%.3f");
        if (ImGui.isItemHovered()) {
            float wheel = ReplayUI.getIO().getMouseWheel();
            if (wheel != 0.0f) {
                double scale = ReplayUI.getIO().getKeyShift() ? 0.1 : ReplayUI.getIO().getKeyCtrl() ? 10.0 : 1.0;
                floatValue.set((float) (floatValue.get() + wheel * step * scale));
                changed = true;
            }
        }
        tooltip(tooltip, tooltipFlags);
        if (changed) update.accept(Math.max(minimum, Math.min(maximum, floatValue.get())));
        return changed;
    }

    private void refreshEntitiesIfNeeded() {
        long now = System.currentTimeMillis();
        long replayTick = adapter.getCurrentReplayTime();
        if (replayTick != entityListTick || now - lastEntityRefresh > 500L) {
            entities = adapter.listEntities(replayTick);
            entityListTick = replayTick;
            lastEntityRefresh = now;
        }
    }

    private void selectTarget(Optional<TargetReference> reference, String missingMessage) {
        if (reference.isPresent()) {
            target = reference.get();
            markChanged();
        } else showAction(missingMessage);
    }

    private void markChanged() {
        boolean hadPreview = previewController.currentPlan() != null || previewController.busy();
        previewController.clear();
        autoRegenerate = hadPreview;
        lastChangeMillis = System.currentTimeMillis();
        markSavePending();
    }

    private void markSavePending() {
        savePending = true;
        lastChangeMillis = System.currentTimeMillis();
    }

    private void handleDeferredActions() {
        long now = System.currentTimeMillis();
        if (savePending && now - lastChangeMillis >= 500L) {
            configManager.save();
            savePending = false;
        }
        if (autoRegenerate && now - lastChangeMillis >= AUTO_PREVIEW_DEBOUNCE_MILLIS && !previewController.busy()) {
            autoRegenerate = false;
            requestPreview();
        }
    }

    private double effectiveDurationSeconds() {
        return resolveInterval().durationSeconds();
    }

    private ReplayIntervalResolver.Resolution resolveInterval() {
        ReplayEditorAdapter.ReplayTimeRange range = adapter.getSelectedTimeRange();
        return intervalResolver.resolve(range.selected(), range.startTick(), range.endTick(),
                adapter.getCurrentReplayTime(), adapter.getTotalReplayTime(), config.durationSeconds);
    }

    private void showAction(String message) {
        actionMessage = message;
        actionMessageUntil = System.currentTimeMillis() + 5000L;
    }

    private static String timestamp(long tick) {
        long millis = Math.max(0L, tick) * 50L;
        long minutes = millis / 60_000L;
        long seconds = (millis / 1000L) % 60L;
        long remainder = millis % 1000L;
        return String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, remainder);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
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

    private static String tr(String key, Object... arguments) {
        return I18n.get(key, arguments);
    }

    private static String localizePathWarning(PathWarning warning) {
        String key = "cinewolf.path_warning." + warning.code();
        String argument = warning.message().replaceAll("\\D+", " ").trim().split(" ")[0];
        String translated = argument.isBlank() ? tr(key) : tr(key, argument);
        return translated.equals(key) ? warning.message() : translated;
    }
}
