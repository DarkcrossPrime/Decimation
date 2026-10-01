package com.decimation.client.mixin;

import com.decimation.client.firstperson.FirstPersonRenderState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorFeatureRendererMixin {
    @Inject(method = "renderArmor(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/entity/EquipmentSlot;ILnet/minecraft/client/render/entity/model/BipedEntityModel;)V",
        at = @At("HEAD"), cancellable = true)
    private void decimation$hideFirstPersonHelmet(MatrixStack matrices,
                                                   VertexConsumerProvider consumers,
                                                   LivingEntity entity, EquipmentSlot slot,
                                                   int light, BipedEntityModel<?> model,
                                                   CallbackInfo callback) {
        if (FirstPersonRenderState.isRenderingPlayer(entity) && slot == EquipmentSlot.HEAD) {
            callback.cancel();
        }
    }
}
