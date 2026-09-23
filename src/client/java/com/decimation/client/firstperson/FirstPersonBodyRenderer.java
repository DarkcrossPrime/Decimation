package com.decimation.client.firstperson;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Renders the local player's real model around the camera during first person. */
public final class FirstPersonBodyRenderer {
    public static final FirstPersonBodyRenderer INSTANCE = new FirstPersonBodyRenderer();
    private static final double CAMERA_FORWARD_OFFSET = 3.0 / 16.0;

    private FirstPersonBodyRenderer() { }

    public void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(this::render);
    }

    private void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (!isActive(client, player)) return;

        MatrixStack matrices = context.matrixStack();
        if (matrices == null || context.consumers() == null) return;

        float tickDelta = context.tickDelta();
        Camera camera = context.camera();
        Vec3d cameraPosition = camera.getPos();
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw);
        double bodyYawRadians = Math.toRadians(bodyYaw);
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
        BipedEntityModel.ArmPose rightArmPose = model.rightArmPose;
        BipedEntityModel.ArmPose leftArmPose = model.leftArmPose;
        matrices.push();
        FirstPersonRenderState.begin(player);
        try {
            dispatcher.render(player, x, y, z, player.getYaw(), tickDelta, matrices,
                context.consumers(), dispatcher.getLight(player, tickDelta));
        } finally {
            FirstPersonRenderState.end();
            model.head.visible = headVisible;
            model.hat.visible = hatVisible;
            model.rightArmPose = rightArmPose;
            model.leftArmPose = leftArmPose;
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
