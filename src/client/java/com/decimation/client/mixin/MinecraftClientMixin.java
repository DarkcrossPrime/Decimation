package com.decimation.client.mixin;

import com.decimation.module.gun.WeaponItem;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void decimation$disableVanillaWeaponAttack(CallbackInfoReturnable<Boolean> callback) {
        if (isHoldingWeapon()) callback.setReturnValue(false);
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void decimation$disableVanillaWeaponBlockBreaking(boolean breaking,
                                                               CallbackInfo callback) {
        if (breaking && isHoldingWeapon()) callback.cancel();
    }

    private boolean isHoldingWeapon() {
        MinecraftClient client = (MinecraftClient) (Object) this;
        return client.player != null
            && client.player.getMainHandStack().getItem() instanceof WeaponItem;
    }
}
