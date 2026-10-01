package com.decimation.client.mixin;

import com.decimation.client.gun.WeaponRenderContext;
import com.decimation.client.firstperson.FirstPersonRenderState;
import com.decimation.module.gun.WeaponItem;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
        at = @At("HEAD"), cancellable = true)
    private void decimation$beginWeaponRender(MatrixStack matrices, VertexConsumerProvider consumers,
                                              int light, LivingEntity entity, float limbAngle,
                                              float limbDistance, float tickDelta,
                                              float animationProgress, float headYaw, float headPitch,
                                              CallbackInfo callback) {
        if (FirstPersonRenderState.isRenderingPlayer(entity)) {
            if (FirstPersonRenderState.isBodyPass() || FirstPersonRenderState.isSupportPass()) {
                callback.cancel();
                return;
            }
            FirstPersonRenderState.beginHeldItems();
        }
        if (hasWeapon(entity)) WeaponRenderContext.begin(entity.getUuid());
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
        at = @At("RETURN"))
    private void decimation$endWeaponRender(MatrixStack matrices, VertexConsumerProvider consumers,
                                            int light, LivingEntity entity, float limbAngle,
                                            float limbDistance, float tickDelta,
                                            float animationProgress, float headYaw, float headPitch,
                                            CallbackInfo callback) {
        if (FirstPersonRenderState.isRenderingPlayer(entity)) {
            if (FirstPersonRenderState.isBodyPass() || FirstPersonRenderState.isSupportPass()) return;
            FirstPersonRenderState.endHeldItems();
        }
        if (hasWeapon(entity)) WeaponRenderContext.end();
    }

    private static boolean hasWeapon(LivingEntity entity) {
        return entity.getMainHandStack().getItem() instanceof WeaponItem
            || entity.getOffHandStack().getItem() instanceof WeaponItem;
    }
}
