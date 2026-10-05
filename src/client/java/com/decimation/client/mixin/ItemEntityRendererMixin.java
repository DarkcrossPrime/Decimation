package com.decimation.client.mixin;

import com.decimation.client.gun.DroppedWeaponAccess;
import com.decimation.module.gun.WeaponItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemEntityRenderer.class, remap = false)
public abstract class ItemEntityRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V", at = @At("TAIL"))
    private void decimation$captureWeapon(ItemEntity entity, ItemEntityRenderState state, float delta, CallbackInfo ci) {
        ((DroppedWeaponAccess) state).decimation$setWeapon(entity.getItem().getItem() instanceof WeaponItem);
    }

    @Redirect(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;getSpin(FF)F"))
    private float decimation$weaponSpin(float age, float bob, ItemEntityRenderState state,
                                       PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        return ((DroppedWeaponAccess) state).decimation$isWeapon() ? 0 : ItemEntity.getSpin(age, bob);
    }

    @Redirect(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private void decimation$groundWeapon(PoseStack target, float x, float y, float z, ItemEntityRenderState state,
                                        PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        if (((DroppedWeaponAccess) state).decimation$isWeapon()) {
            var bounds = state.item.getModelBoundingBox();
            // Bounds include the native item-layer centering. Remove all hover/bob and center on the entity.
            target.translate(-(bounds.minX + bounds.maxX) / 2, -bounds.minY, -(bounds.minZ + bounds.maxZ) / 2);
        } else target.translate(x, y, z);
    }
}
