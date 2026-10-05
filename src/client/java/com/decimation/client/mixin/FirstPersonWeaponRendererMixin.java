package com.decimation.client.mixin;

import com.decimation.module.gun.WeaponItem;
import com.decimation.client.firstperson.FirstPersonFrameAccess;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Main-hand weapons submit their extracted camera-space model without vanilla swing/hand transforms. */
@Mixin(value = FirstPersonHandsAndItemsRenderer.class, remap = false)
public abstract class FirstPersonWeaponRendererMixin {
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void decimation$weapon(float partialTick, PoseStack poses, SubmitNodeCollector collector,
                                  PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands, CallbackInfo callback) {
        if (((FirstPersonFrameAccess) player).decimation$getFrame() != null) { callback.cancel();return; }
        if (hands.mainHandItem == null || !(hands.mainHandItem.getItem() instanceof WeaponItem)) return;
        if (player.avatarRenderState != null && !hands.isScoping) {
            hands.mainHandRenderState.submit(poses, collector, player.avatarRenderState.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        }
        callback.cancel();
    }
}
