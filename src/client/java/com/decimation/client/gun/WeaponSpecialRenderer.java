package com.decimation.client.gun;

import com.decimation.client.content.BakedObjMesh;
import com.decimation.client.content.DanimFrames;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Submission/draw work reads only baked geometry and the immutable extraction snapshot. */
public final class WeaponSpecialRenderer implements SpecialModelRenderer<WeaponSpecialRenderer.Frame> {
    private final WeaponVisualData data;
    private final RenderType normal, glint, outline;

    public WeaponSpecialRenderer(WeaponVisualData data) {
        this.data = data;
        Identifier texture = Identifier.parse(data.definition().assets().texture());
        normal = RenderTypes.entityCutout(texture);
        glint = RenderTypes.itemCutoutGlint(texture);
        outline = RenderTypes.outline(texture);
    }

    @Override
    public void submit(Frame frame, PoseStack poses, SubmitNodeCollector collector, int light, int overlay, boolean foil, int outlineColor) {
        if (frame == null) return;
        var animation = frame.sample();
        float rackFrame = animation.frame() - Math.max(0, data.definition().reloadTicks() - data.rack().length());
        boolean rack = animation.kind() == ClientWeaponPresentation.Kind.RELOAD && animation.rack() && rackFrame >= 0;
        poses.pushPose();
        try {
            poses.mulPose(frame.transform());
            applyRoot(poses, data, animation, frame.reloadRotationInHand());
            for (BakedObjMesh.Part part : data.mesh().parts()) {
                poses.pushPose();
                try {
                    if (animation.kind() == ClientWeaponPresentation.Kind.FIRE) apply(poses, part.tracks().fire(), animation.frame());
                    if (animation.kind() == ClientWeaponPresentation.Kind.RELOAD) apply(poses, part.tracks().reload(), animation.frame());
                    if (rack) apply(poses, part.tracks().rack(), rackFrame);
                    collector.submitCustomGeometry(poses, foil ? glint : normal,
                        (pose, consumer) -> emit(part, pose, consumer, light, overlay, -1));
                    if (outlineColor != 0) collector.submitCustomGeometry(poses, outline,
                        (pose, consumer) -> emit(part, pose, consumer, light, overlay, outlineColor));
                } finally { poses.popPose(); }
            }
        } finally { poses.popPose(); }
    }

    private static void emit(BakedObjMesh.Part part, PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int color) {
        for (int index = 0; index < part.cornerCount(); index++) {
            var vertex = part.corner(index);
            consumer.addVertex(pose, vertex.x(), vertex.y(), vertex.z()).setColor(color).setUv(vertex.u(), vertex.v())
                .setOverlay(overlay).setLight(light).setNormal(pose, vertex.nx(), vertex.ny(), vertex.nz());
        }
    }

    private static void apply(PoseStack poses, DanimFrames.Track track, float frame) {
        apply(poses, track, frame, false);
    }
    private static void apply(PoseStack poses, DanimFrames.Track track, float frame, boolean reloadRoot) {
        apply(poses, track, frame, reloadRoot, false);
    }
    private static void apply(PoseStack poses, DanimFrames.Track track, float frame, boolean reloadRoot, boolean translationOnly) {
        var motion = track.sample(frame);
        if (motion.equals(DanimFrames.Motion.IDENTITY)) return;
        // ANIB kept model-Y-down coordinates while the OBJ exporter already flipped Y.
        // Preserve its authored left-hand reload path instead of flipping it to the right.
        // Native OBJ clips retain checkpoint 6.6's Z reflection. Convert after interpolation.
        poses.translate(motion.x(), track.modelYDown() ? -motion.y() : motion.y(), track.modelYDown() ? motion.z() : -motion.z());
        if (translationOnly) return;
        // Reload Model's X is barrel roll and Z is muzzle elevation. Both tilt opposite
        // to the component-track convention; keep translations and component motion intact.
        boolean reverseTilt = reloadRoot && track.modelYDown();
        poses.rotateDegrees(Axis.XP, reverseTilt ? motion.pitch() : -motion.pitch());
        poses.rotateDegrees(Axis.YP, track.modelYDown() ? motion.yaw() : -motion.yaw());
        poses.rotateDegrees(Axis.ZP, track.modelYDown() && !reverseTilt ? -motion.roll() : motion.roll());
    }

    /** The support target uses exactly the same animated root as the submitted gun. */
    public static void applyRoot(PoseStack poses, WeaponVisualData data, ClientWeaponPresentation.Sample animation) {
        applyRoot(poses, data, animation, false);
    }
    public static void applyRoot(PoseStack poses, WeaponVisualData data, ClientWeaponPresentation.Sample animation, boolean reloadRotationInHand) {
        if (animation.kind() == ClientWeaponPresentation.Kind.FIRE) apply(poses, data.fire().track("Model"), animation.frame());
        if (animation.kind() == ClientWeaponPresentation.Kind.RELOAD) {
            apply(poses, data.reload().track("Model"), animation.frame(), true, reloadRotationInHand);
            float frame = animation.frame() - Math.max(0, data.definition().reloadTicks() - data.rack().length());
            if (animation.rack() && frame >= 0) apply(poses, data.rack().track("Model"), frame, false, reloadRotationInHand);
        }
    }

    public static void applyReloadPart(PoseStack poses, DanimFrames.Track track, float frame) { apply(poses, track, frame); }

    @Override public Frame extractArgument(ItemStack stack) { return null; } // WeaponItemModel supplies the extracted argument directly.
    @Override public void getExtents(Consumer<Vector3fc> output) {
        var bounds = data.mesh().bounds();
        output.accept(new Vector3f(bounds.minX(), bounds.minY(), bounds.minZ()));
        output.accept(new Vector3f(bounds.maxX(), bounds.maxY(), bounds.maxZ()));
    }
    public record Frame(Matrix4fc transform, ClientWeaponPresentation.Sample sample, boolean reloadRotationInHand) {
        public Frame(Matrix4fc transform, ClientWeaponPresentation.Sample sample) { this(transform, sample, false); }
    }
}
