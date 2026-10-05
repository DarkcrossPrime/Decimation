package com.decimation.client.mixin;

import com.decimation.client.gun.WeaponAdsPresentation;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Modify the calculated FOV before Camera caches projection; getFov alone would be too late. */
@Mixin(value = Camera.class, remap = false)
public abstract class WeaponAdsCameraMixin {
    @Inject(method = "calculateFov(F)F", at = @At("RETURN"), cancellable = true)
    private void decimation$ads(float partialTick, CallbackInfoReturnable<Float> callback) {
        float aim = WeaponAdsPresentation.progress((Camera) (Object) this, partialTick);
        if (aim > 0) callback.setReturnValue(WeaponAdsPresentation.fieldOfView(callback.getReturnValue(), aim));
    }
}
