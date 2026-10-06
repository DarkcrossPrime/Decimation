package com.decimation.client.mixin;

import com.decimation.client.firstperson.WeaponPlayerPose;
import com.decimation.client.firstperson.WeaponPoseAccess;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PlayerModel.class, remap = false)
public abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void decimation$arms(AvatarRenderState state, CallbackInfo ci) {
        WeaponPlayerPose.external((PlayerModel) (Object) this, state, ((WeaponPoseAccess) state).decimation$getMotion());
    }
}
