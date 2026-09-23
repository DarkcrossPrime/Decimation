package com.decimation.client.gun;

import com.decimation.client.content.ClientContentManager;
import com.decimation.client.content.DanimAnimation;
import com.decimation.client.content.ObjModel;
import com.decimation.client.firstperson.FirstPersonRenderState;
import com.decimation.module.gun.WeaponItem;
import com.decimation.module.gun.data.WeaponDefinition;
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
    private final WeaponDefinition definition;

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
        VertexConsumer vertices = consumers.getBuffer(RenderLayer.getEntityCutout(definition.assets().texture()));
        DanimAnimation animation = activeAnimation();
        int frame = ClientWeaponController.INSTANCE.animation() == null ? 0
            : ClientWeaponController.INSTANCE.animation().frame(MinecraftClient.getInstance());
        for (ObjModel.Face face : model.faces()) {
            matrices.push();
            if (animation != null) {
                applyAnimationTransform(matrices, DanimSampler.sample(animation, "Model", frame));
                applyAnimationTransform(matrices, DanimSampler.sample(animation, face.object(), frame));
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

    private DanimAnimation activeAnimation() {
        ClientWeaponController.ActiveAnimation active = ClientWeaponController.INSTANCE.animation();
        if (active == null) return null;
        return ClientContentManager.INSTANCE.animations().get(active.id());
    }

    private static void applyViewTransform(MatrixStack matrices, ModelTransformationMode mode) {
        if (mode == ModelTransformationMode.GUI) {
            matrices.translate(0.5, 0.45, 0.0);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(140));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-18));
            matrices.scale(0.035f, 0.035f, 0.035f);
        } else if (mode.isFirstPerson()) {
            ClientWeaponController controller = ClientWeaponController.INSTANCE;
            float ads = controller.adsProgress();
            matrices.translate(0.55 - 0.27 * ads, 0.35 - 0.08 * ads, -0.55 - 0.2 * ads);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-controller.recoilPitch()));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(controller.recoilYaw()));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
            matrices.scale(0.03f, 0.03f, 0.03f);
        } else if (FirstPersonRenderState.isRenderingBody()) {
            ClientWeaponController controller = ClientWeaponController.INSTANCE;
            float ads = controller.adsProgress();
            matrices.translate(0.48, 0.50 - 0.04 * ads, 0.50 - 0.04 * ads);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-controller.recoilPitch()));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(controller.recoilYaw()));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
            matrices.scale(0.025f, 0.025f, 0.025f);
        } else {
            matrices.translate(0.5, 0.5, 0.5);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
            matrices.scale(0.025f, 0.025f, 0.025f);
        }
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
        for (int corner = 1; corner < indices.length - 1; corner++) {
            emitTriangle(model, face, matrix, consumer, light, overlay, 0, corner, corner + 1);
        }
    }

    private static void emitTriangle(ObjModel model, ObjModel.Face face, MatrixStack.Entry matrix,
                                     VertexConsumer consumer, int light, int overlay, int a, int b, int c) {
        ObjModel.Vertex va = model.vertices().get(face.vertexIndices()[a]);
        ObjModel.Vertex vb = model.vertices().get(face.vertexIndices()[b]);
        ObjModel.Vertex vc = model.vertices().get(face.vertexIndices()[c]);
        Vec3d ab = new Vec3d(vb.x() - va.x(), vb.y() - va.y(), vb.z() - va.z());
        Vec3d ac = new Vec3d(vc.x() - va.x(), vc.y() - va.y(), vc.z() - va.z());
        Vec3d normal = ab.crossProduct(ac).normalize();
        emitVertex(model, face, a, matrix, consumer, light, overlay, normal);
        emitVertex(model, face, b, matrix, consumer, light, overlay, normal);
        emitVertex(model, face, c, matrix, consumer, light, overlay, normal);
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
