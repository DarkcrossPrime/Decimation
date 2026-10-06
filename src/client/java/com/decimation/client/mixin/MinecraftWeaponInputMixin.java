package com.decimation.client.mixin;

import com.decimation.client.gun.ClientWeaponController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Weapon mouse buttons are intents, including when looking at empty space. */
@Mixin(value = Minecraft.class, remap = false)
public abstract class MinecraftWeaponInputMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void decimation$attack(CallbackInfoReturnable<Boolean> callback) {
        if (ClientWeaponController.suppressesVanillaInput()) callback.setReturnValue(false);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void decimation$continueAttack(boolean attacking, CallbackInfo callback) {
        if (ClientWeaponController.suppressesVanillaInput()) {
            Minecraft client = Minecraft.getInstance();
            if (client.gameMode != null && client.gameMode.isDestroying()) client.gameMode.stopDestroyBlock();
            callback.cancel();
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void decimation$use(CallbackInfo callback) {
        if (ClientWeaponController.suppressesVanillaInput()) callback.cancel();
    }
}
