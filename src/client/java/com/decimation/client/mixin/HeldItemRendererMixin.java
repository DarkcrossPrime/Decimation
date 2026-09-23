package com.decimation.client.mixin;

import com.decimation.client.firstperson.FirstPersonBodyRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
        at = @At("HEAD"), cancellable = true)
    private void decimation$hideVanillaFirstPersonHands(float tickDelta, MatrixStack matrices,
                                                        VertexConsumerProvider.Immediate consumers,
                                                        ClientPlayerEntity player, int light,
                                                        CallbackInfo callback) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (FirstPersonBodyRenderer.isActive(client, player)) {
            callback.cancel();
        }
    }
}
