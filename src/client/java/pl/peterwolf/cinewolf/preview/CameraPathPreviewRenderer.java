package pl.peterwolf.cinewolf.preview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import pl.peterwolf.cinewolf.integration.flashback.FlashbackReplayEditorAdapter;
import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.PathWarning;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.List;
import java.util.ArrayList;

public final class CameraPathPreviewRenderer {
    private static final int MAX_RENDERED_SAMPLES = 512;
    private final FlashbackReplayEditorAdapter adapter;
    private volatile RenderState state;
    private volatile boolean visible = true;

    public CameraPathPreviewRenderer(FlashbackReplayEditorAdapter adapter) {
        this.adapter = adapter;
    }

    public void register() {
        LevelRenderEvents.BEFORE_GIZMOS.register(this::render);
    }

    public void setPlan(CameraPathPlan plan) {
        setPlans(List.of(plan));
    }

    public void setPlans(List<CameraPathPlan> plans) {
        int totalSamples = plans.stream().mapToInt(plan -> plan.samples().size()).sum();
        int stride = Math.max(1, (int) Math.ceil(totalSamples / (double) MAX_RENDERED_SAMPLES));
        List<List<CameraSample>> shots = new ArrayList<>();
        List<PathWarning> warnings = new ArrayList<>();
        for (CameraPathPlan plan : plans) {
            List<CameraSample> renderSamples = new ArrayList<>();
            for (int index = 0; index < plan.samples().size(); index += stride) {
                renderSamples.add(plan.samples().get(index));
            }
            if (!plan.samples().isEmpty() && (renderSamples.isEmpty()
                    || renderSamples.getLast() != plan.samples().getLast())) {
                renderSamples.add(plan.samples().getLast());
            }
            if (!renderSamples.isEmpty()) shots.add(List.copyOf(renderSamples));
            warnings.addAll(plan.warnings());
        }
        state = shots.isEmpty() ? null : new RenderState(shots, warnings);
    }

    public void clear() {
        state = null;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    private void render(LevelRenderContext context) {
        RenderState renderState = state;
        if (!visible || renderState == null || renderState.shots().isEmpty() || !adapter.isReplayEditorOpen()) return;
        Vec3 camera = context.levelState().cameraRenderState.pos;
        PoseStack poseStack = context.poseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(),
                (pose, consumer) -> draw(renderState, pose, consumer));
        poseStack.popPose();
    }

    private static void draw(RenderState state, PoseStack.Pose pose, VertexConsumer consumer) {
        int[] colours = {0xFF36C6F4, 0xFFFFB347, 0xFF8B5CF6, 0xFF42F57B, 0xFFFF6B9D};
        for (int index = 0; index < state.shots().size(); index++) {
            drawShot(state.shots().get(index), pose, consumer, colours[index % colours.length], state.warnings());
        }
    }

    private static void drawShot(List<CameraSample> samples, PoseStack.Pose pose, VertexConsumer consumer,
                                 int pathColour, List<PathWarning> warnings) {
        if (samples.size() < 2) return;
        for (int i = 1; i < samples.size(); i++) {
            int color = samples.get(i).discontinuity() ? 0xFFFF3EA5 : pathColour;
            line(pose, consumer, samples.get(i - 1).position(), samples.get(i).position(), color, 2.5f);
        }

        marker(pose, consumer, samples.getFirst().position(), 0.28, 0xFF42F57B, 3.5f);
        marker(pose, consumer, samples.getLast().position(), 0.28, 0xFFF24848, 3.5f);
        marker(pose, consumer, samples.getFirst().lookAtPoint(), 0.35, 0xFFFFD166, 3.0f);

        int markerStep = Math.max(1, samples.size() / 12);
        for (int i = 0; i < samples.size(); i += markerStep) {
            CameraSample sample = samples.get(i);
            marker(pose, consumer, sample.position(), 0.12, 0xFFBDEFFF, 1.5f);
            Vec3d lookEnd = sample.position().lerp(sample.lookAtPoint(), 0.18);
            line(pose, consumer, sample.position(), lookEnd, 0x889EE7FF, 1.0f);
        }

        int arrowStep = Math.max(2, samples.size() / 8);
        for (int i = arrowStep; i < samples.size(); i += arrowStep) {
            Vec3d end = samples.get(i).position();
            Vec3d direction = end.subtract(samples.get(i - 1).position()).normalizeOr(new Vec3d(0, 0, 1));
            Vec3d side = Vec3d.UP.cross(direction).normalizeOr(new Vec3d(1, 0, 0));
            Vec3d base = end.subtract(direction.multiply(0.45));
            line(pose, consumer, end, base.add(side.multiply(0.22)), pathColour, 2.0f);
            line(pose, consumer, end, base.subtract(side.multiply(0.22)), pathColour, 2.0f);
        }

        if (warnings.stream().anyMatch(warning -> warning.severity() == PathWarning.Severity.ERROR)) {
            for (CameraSample sample : samples) {
                if (!sample.isFinite()) marker(pose, consumer, sample.position(), 0.5, 0xFFFF00FF, 4.0f);
            }
        }
    }

    private static void marker(PoseStack.Pose pose, VertexConsumer consumer, Vec3d center, double radius, int color, float width) {
        line(pose, consumer, center.add(new Vec3d(-radius, 0, 0)), center.add(new Vec3d(radius, 0, 0)), color, width);
        line(pose, consumer, center.add(new Vec3d(0, -radius, 0)), center.add(new Vec3d(0, radius, 0)), color, width);
        line(pose, consumer, center.add(new Vec3d(0, 0, -radius)), center.add(new Vec3d(0, 0, radius)), color, width);
    }

    private static void line(PoseStack.Pose pose, VertexConsumer consumer, Vec3d start, Vec3d end, int color, float width) {
        Vec3d normal = end.subtract(start).cross(Vec3d.UP).normalizeOr(new Vec3d(1, 0, 0));
        vertex(pose, consumer, start, normal, color, width);
        vertex(pose, consumer, end, normal, color, width);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, Vec3d point, Vec3d normal, int color, float width) {
        consumer.addVertex(pose, (float) point.x(), (float) point.y(), (float) point.z())
                .setColor(color)
                .setNormal(pose, (float) normal.x(), (float) normal.y(), (float) normal.z())
                .setLineWidth(width);
    }

    private record RenderState(List<List<CameraSample>> shots, List<PathWarning> warnings) {
        private RenderState {
            shots = shots.stream().map(List::copyOf).toList();
            warnings = List.copyOf(warnings);
        }
    }
}
