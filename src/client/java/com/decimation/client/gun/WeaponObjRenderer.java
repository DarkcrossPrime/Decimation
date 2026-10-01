package com.decimation.client.gun;

import com.decimation.client.content.ClientContentManager;
import com.decimation.client.content.DanimAnimation;
import com.decimation.client.content.ObjModel;
import com.decimation.client.firstperson.FirstPersonRenderState;
import com.decimation.client.firstperson.FirstPersonSupportArmSolver;
import com.decimation.module.gun.WeaponItem;
import com.decimation.module.gun.data.WeaponDefinition;
import com.decimation.module.gun.data.WeaponTransform;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class WeaponObjRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    /** Converts the recovered Decimation model axis (+X forward) to Minecraft held-item forward. */
    private static final float MODEL_FORWARD_YAW = 90.0f;
    private static final double FIRST_PERSON_TOWARD_PLAYER = 3.0 / 11.0;
    private static final float FIRST_PERSON_SIZE_MULTIPLIER = 1.32f;
    private static final float SUPPORT_HAND_FORWARD_PIXELS = 1.0f;
    private static final float SUPPORT_HAND_LOWER_PIXELS = 2.5f;
    private final WeaponDefinition definition;
    private ObjModel gripModel;
    private FirstPersonSupportArmSolver.Grip supportGrip;
    private float supportGripAdvance;
    private float supportGripDrop;

    private WeaponObjRenderer(WeaponDefinition definition) {
        this.definition = definition;
    }

    public static void register(WeaponItem item) {
        BuiltinItemRendererRegistry.INSTANCE.register(item, new WeaponObjRenderer(item.definition()));
    }

    @Override
    public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                       VertexConsumerProvider consumers, int light, int overlay) {
        if (mode == ModelTransformationMode.GUI) {
            renderInventoryIcon(matrices, consumers, light, overlay);
            return;
        }
        ObjModel model = ClientContentManager.INSTANCE.models().get(definition.assets().model());
        if (model == null) return;
        matrices.push();
        applyViewTransform(matrices, mode);
        VertexConsumer vertices = consumers.getBuffer(
            RenderLayer.getEntityCutoutNoCull(definition.assets().texture()));
        ClientWeaponController.ActiveAnimation active = activeAnimation();
        DanimAnimation animation = active == null ? null
            : ClientContentManager.INSTANCE.animations().get(active.id());
        MinecraftClient client = MinecraftClient.getInstance();
        float frame = active == null ? 0 : active.frame(client, client.getTickDelta());
        DanimSampler.Transform modelTransform = animation == null ? DanimSampler.Transform.IDENTITY
            : DanimSampler.sample(animation, "Model", frame);
        if (FirstPersonRenderState.isRigPass() && !FirstPersonRenderState.isSupportPass()) {
            if (gripModel != model) {
                gripModel = model;
                supportGrip = FirstPersonSupportArmSolver.midBarrelGrip(model);
            }
            matrices.push();
            if (animation != null) applyAnimationTransform(matrices, modelTransform);
            FirstPersonSupportArmSolver.Grip adjustedGrip = supportGrip == null ? null
                : new FirstPersonSupportArmSolver.Grip(supportGrip.x() + supportGripAdvance,
                    supportGrip.y() - supportGripDrop, supportGrip.z());
            FirstPersonRenderState.captureSupportGrip(stack, matrices, adjustedGrip);
            matrices.pop();
        }
        Map<String, DanimSampler.Transform> partTransforms = new HashMap<>();
        for (ObjModel.Face face : model.faces()) {
            matrices.push();
            if (animation != null) {
                applyAnimationTransform(matrices, modelTransform);
                applyAnimationTransform(matrices, partTransforms.computeIfAbsent(face.object(),
                    name -> DanimSampler.sample(animation, name, frame)));
            }
            emitFace(model, face, matrices.peek(), vertices, light, overlay);
            matrices.pop();
        }
        matrices.pop();
    }

    private void renderInventoryIcon(MatrixStack matrices, VertexConsumerProvider consumers,
                                     int light, int overlay) {
        VertexConsumer vertices = consumers.getBuffer(
            RenderLayer.getEntityCutoutNoCull(definition.assets().itemTexture()));
        MatrixStack.Entry matrix = matrices.peek();
        Matrix4f position = matrix.getPositionMatrix();
        Matrix3f normal = matrix.getNormalMatrix();
        iconVertex(vertices, position, normal, 0, 1, 0, 0, light, overlay);
        iconVertex(vertices, position, normal, 1, 1, 1, 0, light, overlay);
        iconVertex(vertices, position, normal, 1, 0, 1, 1, light, overlay);
        iconVertex(vertices, position, normal, 0, 0, 0, 1, light, overlay);
    }

    private static void iconVertex(VertexConsumer vertices, Matrix4f position, Matrix3f normal,
                                   float x, float y, float u, float v, int light, int overlay) {
        vertices.vertex(position, x, y, 0.5f).color(255, 255, 255, 255)
            .texture(u, v).overlay(overlay).light(light).normal(normal, 0, 0, 1).next();
    }

    private ClientWeaponController.ActiveAnimation activeAnimation() {
        return ClientWeaponController.INSTANCE.animation(WeaponRenderContext.owner(), definition.id());
    }

    private void applyViewTransform(MatrixStack matrices, ModelTransformationMode mode) {
        if (FirstPersonRenderState.isRenderingBody()) {
            ClientWeaponController controller = ClientWeaponController.INSTANCE;
            float tickDelta = MinecraftClient.getInstance().getTickDelta();
            WeaponTransform held = WeaponTransform.interpolate(
                definition.presentation().firstPersonHip(),
                definition.presentation().firstPersonAds(),
                controller.adsProgress(tickDelta));
            WeaponTransform transform = WeaponTransform.interpolate(held,
                definition.presentation().firstPersonSprint(), controller.sprintProgress(tickDelta));
            float hipWeight = (1.0f - controller.adsProgress(tickDelta))
                * (1.0f - controller.sprintProgress(tickDelta));
            matrices.translate(FirstPersonRenderState.HIP_WEAPON_RIGHT * hipWeight,
                -FirstPersonRenderState.HIP_WEAPON_LOWER * hipWeight,
                FIRST_PERSON_TOWARD_PLAYER
                    + FirstPersonRenderState.HIP_WEAPON_BACKSET * hipWeight);
            // The firing arm owns local recoil so the support hand can follow it.
            applyTransform(matrices, transform, 0, 0,
                FirstPersonRenderState.HIP_WEAPON_PITCH * hipWeight);
            float size = FIRST_PERSON_SIZE_MULTIPLIER
                * (1.0f + (FirstPersonRenderState.HIP_WEAPON_SCALE - 1.0f) * hipWeight);
            // OBJ +Y is up. Convert model pixels to OBJ units before lowering
            // the grip, keeping the drop constant as weapon presentation scale changes.
            supportGripDrop = SUPPORT_HAND_LOWER_PIXELS / (16.0f * transform.scale() * size);
            matrices.scale(size, size, size);
            // The recovered OBJ points down +X. Match the original hip view's
            // shorter barrel axis without changing the receiver's width or height.
            float lengthScale = 1.0f
                + (FirstPersonRenderState.HIP_WEAPON_LENGTH_SCALE - 1.0f) * hipWeight;
            // OBJ +X points toward the muzzle; compensate for barrel shortening
            // so the hand advances by the same model pixel at every presentation scale.
            supportGripAdvance = SUPPORT_HAND_FORWARD_PIXELS
                / (16.0f * transform.scale() * size * lengthScale);
            matrices.scale(lengthScale, 1.0f, 1.0f);
        } else if (mode.isFirstPerson()) {
            ClientWeaponController controller = ClientWeaponController.INSTANCE;
            float ads = controller.adsProgress(MinecraftClient.getInstance().getTickDelta());
            matrices.translate(0.55 - 0.27 * ads, 0.35 - 0.08 * ads, -0.55 - 0.2 * ads);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-controller.recoilPitch()));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(controller.recoilYaw()));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MODEL_FORWARD_YAW));
            matrices.scale(0.03f, 0.03f, 0.03f);
        } else {
            applyTransform(matrices, definition.presentation().thirdPerson(), 0, 0);
        }
    }

    private static void applyTransform(MatrixStack matrices, WeaponTransform transform,
                                       float recoilPitch, float recoilYaw) {
        applyTransform(matrices, transform, recoilPitch, recoilYaw, 0);
    }

    private static void applyTransform(MatrixStack matrices, WeaponTransform transform,
                                       float recoilPitch, float recoilYaw, float extraPitch) {
        matrices.translate(transform.x(), transform.y(), transform.z());
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-recoilPitch));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(recoilYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(transform.pitch() + extraPitch));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(transform.yaw()));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(transform.roll()));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MODEL_FORWARD_YAW));
        matrices.scale(transform.scale(), transform.scale(), transform.scale());
    }

    private static void applyAnimationTransform(MatrixStack matrices, DanimSampler.Transform transform) {
        float[] position = transform.position();
        float[] rotation = transform.rotation();
        matrices.translate(position[0], position[1], position[2]);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotation[0]));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotation[1]));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation[2]));
    }

    private static void emitFace(ObjModel model, ObjModel.Face face, MatrixStack.Entry matrix,
                                 VertexConsumer consumer, int light, int overlay) {
        int[] indices = face.vertexIndices();
        if (indices.length < 3) return;
        Vec3d normal = faceNormal(model, face);
        if (normal.lengthSquared() < 1.0E-12) return;

        // Minecraft's entity render layers use DrawMode.QUADS. The recovered weapon OBJs are
        // quad-based too, so submit their four vertices directly. Feeding two three-vertex
        // triangles into this buffer makes the fourth vertex of one primitive consume the first
        // vertex of the next, which joins unrelated model parts into the shredded geometry seen
        // on screen.
        if (indices.length == 4) {
            for (int corner = 0; corner < 4; corner++) {
                emitVertex(model, face, corner, matrix, consumer, light, overlay, normal);
            }
            return;
        }

        // Keep arbitrary OBJ polygons safe on a quad buffer. Repeating the final triangle vertex
        // creates a degenerate fourth corner without allowing primitives to cross face boundaries.
        for (int corner = 1; corner < indices.length - 1; corner++) {
            emitVertex(model, face, 0, matrix, consumer, light, overlay, normal);
            emitVertex(model, face, corner, matrix, consumer, light, overlay, normal);
            emitVertex(model, face, corner + 1, matrix, consumer, light, overlay, normal);
            emitVertex(model, face, corner + 1, matrix, consumer, light, overlay, normal);
        }
    }

    /**
     * Computes one normal for the original OBJ polygon instead of one normal per emitted triangle.
     * The converted Beardie models contain slightly non-planar quads; lighting each half separately
     * exposes their triangulation as dark wedges. Newell's method keeps the legacy quad visually flat.
     */
    private static Vec3d faceNormal(ObjModel model, ObjModel.Face face) {
        int[] indices = face.vertexIndices();
        double x = 0;
        double y = 0;
        double z = 0;
        for (int index = 0; index < indices.length; index++) {
            ObjModel.Vertex current = model.vertices().get(indices[index]);
            ObjModel.Vertex next = model.vertices().get(indices[(index + 1) % indices.length]);
            x += (current.y() - next.y()) * (current.z() + next.z());
            y += (current.z() - next.z()) * (current.x() + next.x());
            z += (current.x() - next.x()) * (current.y() + next.y());
        }
        Vec3d normal = new Vec3d(x, y, z);
        return normal.lengthSquared() < 1.0E-12 ? Vec3d.ZERO : normal.normalize();
    }

    private static void emitVertex(ObjModel model, ObjModel.Face face, int corner, MatrixStack.Entry matrix,
                                   VertexConsumer consumer, int light, int overlay, Vec3d normal) {
        ObjModel.Vertex vertex = model.vertices().get(face.vertexIndices()[corner]);
        float u = 0;
        float v = 0;
        if (face.textureIndices()[corner] >= 0) {
            ObjModel.TextureCoordinate texture = model.textureCoordinates().get(face.textureIndices()[corner]);
            u = texture.u();
            v = 1.0f - texture.v();
        }
        Matrix4f positionMatrix = matrix.getPositionMatrix();
        Matrix3f normalMatrix = matrix.getNormalMatrix();
        consumer.vertex(positionMatrix, vertex.x(), vertex.y(), vertex.z())
            .color(255, 255, 255, 255).texture(u, v).overlay(overlay).light(light)
            .normal(normalMatrix, (float) normal.x, (float) normal.y, (float) normal.z).next();
    }
}
