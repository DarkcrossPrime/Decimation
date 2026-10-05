package com.decimation.client.mixin;

import com.decimation.client.firstperson.WeaponPoseAccess;
import com.decimation.client.gun.ClientWeaponPresentation;
import com.decimation.client.gun.WeaponMotion;
import com.decimation.module.gun.WeaponItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AvatarRenderer.class, remap = false)
public abstract class AvatarRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void decimation$captureMotion(Avatar entity, AvatarRenderState state, float delta, CallbackInfo ci) {
        var motion = WeaponMotion.Snapshot.REST;
        var animation = ClientWeaponPresentation.Sample.REST;
        if (state.getMainHandItemStack().getItem() instanceof WeaponItem weapon) {
            animation = ClientWeaponPresentation.sample(entity, Identifier.parse(weapon.definition().id()), delta, entity == Minecraft.getInstance().player);
            motion = animation.motion();
        }
        ((WeaponPoseAccess) state).decimation$setMotion(motion);
        ((WeaponPoseAccess) state).decimation$setAnimation(animation);
    }
}
