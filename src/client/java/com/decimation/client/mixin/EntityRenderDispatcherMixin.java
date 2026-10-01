package com.decimation.client.mixin;

import com.decimation.client.firstperson.FirstPersonRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dispatcher effects belong to the body pass, never the arm/gun overlay. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private void decimation$bodyFireOnly(MatrixStack matrices, VertexConsumerProvider consumers,
                                         Entity entity, CallbackInfo callback) {
        if (FirstPersonRenderState.isRigPass()
            && FirstPersonRenderState.isRenderingPlayer(entity)) callback.cancel();
    }

    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true)
    private static void decimation$bodyShadowOnly(MatrixStack matrices,
                                                   VertexConsumerProvider consumers, Entity entity,
                                                   float opacity, float tickDelta, WorldView world,
                                                   float radius, CallbackInfo callback) {
        if (FirstPersonRenderState.isRigPass()
            && FirstPersonRenderState.isRenderingPlayer(entity)) callback.cancel();
    }

    @Inject(method = "renderHitbox", at = @At("HEAD"), cancellable = true)
    private static void decimation$bodyHitboxOnly(MatrixStack matrices, VertexConsumer vertices,
                                                   Entity entity, float tickDelta,
                                                   CallbackInfo callback) {
        if (FirstPersonRenderState.isRigPass()
            && FirstPersonRenderState.isRenderingPlayer(entity)) callback.cancel();
    }
}
