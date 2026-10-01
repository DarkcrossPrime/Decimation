package com.decimation.client.firstperson;

import com.decimation.module.gun.WeaponItem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/** Renders the local player's real model around the camera during first person. */
public final class FirstPersonBodyRenderer {
    public static final FirstPersonBodyRenderer INSTANCE = new FirstPersonBodyRenderer();
    // Render the body behind the eye along horizontal body yaw. Pitch must not
    // turn this separation into a vertical camera displacement.
    private static final double CAMERA_FORWARD_OFFSET = 3.0 / 16.0;
    private final FirstPersonRigLayer rigLayer = new FirstPersonRigLayer();

    private FirstPersonBodyRenderer() { }

    public void register() {
        rigLayer.register();
        WorldRenderEvents.AFTER_ENTITIES.register(this::render);
    }

    private void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (!isActive(client, player)) return;

        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;

        float tickDelta = context.tickDelta();
        Camera camera = context.camera();
        Vec3d cameraPosition = camera.getPos();
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw);
        float headYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevHeadYaw, player.headYaw);
        boolean holdingWeapon = player.getMainHandStack().getItem() instanceof WeaponItem;
        float towardView = holdingWeapon
            ? MathHelper.wrapDegrees(headYaw - bodyYaw)
                * (1.0f - FirstPersonRenderState.LOOK_ROTATION_FACTOR)
            : 0.0f;
        double bodyYawRadians = Math.toRadians(bodyYaw + towardView);
        double bodyOffsetX = Math.sin(bodyYawRadians) * CAMERA_FORWARD_OFFSET;
        double bodyOffsetZ = -Math.cos(bodyYawRadians) * CAMERA_FORWARD_OFFSET;
        double x = MathHelper.lerp(tickDelta, player.prevX, player.getX()) - cameraPosition.x
            + bodyOffsetX;
        double y = MathHelper.lerp(tickDelta, player.prevY, player.getY()) - cameraPosition.y;
        double z = MathHelper.lerp(tickDelta, player.prevZ, player.getZ()) - cameraPosition.z
            + bodyOffsetZ;

        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        EntityRenderer<? super ClientPlayerEntity> renderer = dispatcher.getRenderer(player);
        if (!(renderer instanceof PlayerEntityRenderer playerRenderer)) return;

        PlayerEntityModel<?> model = playerRenderer.getModel();
        boolean headVisible = model.head.visible;
        boolean hatVisible = model.hat.visible;
        float originalHeadYaw = model.head.yaw;
        float originalHeadPitch = model.head.pitch;
        BipedEntityModel.ArmPose rightArmPose = model.rightArmPose;
        BipedEntityModel.ArmPose leftArmPose = model.leftArmPose;
        Framebuffer main = client.getFramebuffer();
        boolean layered = holdingWeapon && rigLayer.captureWorldDepth(main);
        matrices.push();
        FirstPersonRenderState.begin(player, camera.getHorizontalPlane().y());
        try {
            int light = dispatcher.getLight(player, tickDelta);
            if (layered) {
                FirstPersonRenderState.setRenderPass(FirstPersonRenderPass.BODY);
                // Let the torso retain native body yaw. Camera-follow belongs
                // to the weapon rig and used to suppress the body's visible turn.
                renderPlayer(dispatcher, player, x, y, z, tickDelta, matrices, light, 0.0f);
                FirstPersonRenderState.setRenderPass(FirstPersonRenderPass.RIG);
                rigLayer.drawRig(main, () -> {
                    renderPlayer(dispatcher, player, x, y, z, tickDelta, matrices, light, towardView);
                    // The gun supplies the grip target first. Draw the support
                    // arm from its torso shoulder into the same world-tested rig buffer.
                    FirstPersonRenderState.setRenderPass(FirstPersonRenderPass.SUPPORT);
                    renderPlayer(dispatcher, player, x, y, z, tickDelta, matrices, light, 0.0f);
                });
                FirstPersonRenderState.setRenderPass(FirstPersonRenderPass.FULL);
                rigLayer.composite(main);
            } else {
                renderPlayer(dispatcher, player, x, y, z, tickDelta, matrices, light, towardView);
            }
        } finally {
            main.beginWrite(true);
            FirstPersonRenderState.end();
            model.head.visible = headVisible;
            model.hat.visible = hatVisible;
            model.head.yaw = originalHeadYaw;
            model.head.pitch = originalHeadPitch;
            model.rightArmPose = rightArmPose;
            model.leftArmPose = leftArmPose;
            matrices.pop();
        }
    }

    /** Flush each pass into its own target before switching framebuffers. */
    private static void renderPlayer(EntityRenderDispatcher dispatcher, ClientPlayerEntity player,
                                      double x, double y, double z, float tickDelta,
                                      MatrixStack matrices, int light, float towardView) {
        matrices.push();
        try {
            if (towardView != 0.0f) {
                // Keep the accepted arm/gun turn independent of torso rotation.
                matrices.translate(x, y, z);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-towardView));
                matrices.translate(-x, -y, -z);
            }
            VertexConsumerProvider.Immediate consumers =
                VertexConsumerProvider.immediate(new BufferBuilder(256));
            dispatcher.render(player, x, y, z, player.getYaw(), tickDelta, matrices, consumers, light);
            consumers.draw();
        } finally {
            matrices.pop();
        }
    }

    public static boolean isActive(MinecraftClient client, ClientPlayerEntity player) {
        return player != null
            && client.options.getPerspective() == Perspective.FIRST_PERSON
            && client.getCameraEntity() == player
            && !player.isSpectator()
            && !player.isSleeping()
            && player.isAlive();
    }
}
