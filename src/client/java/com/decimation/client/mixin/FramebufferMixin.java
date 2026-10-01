package com.decimation.client.mixin;

import com.decimation.client.firstperson.FirstPersonRigLayer;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps native item/glint target changes inside the active first-person rig buffer. */
@Mixin(Framebuffer.class)
public abstract class FramebufferMixin {
    @Inject(method = "beginWrite", at = @At("HEAD"), cancellable = true)
    private void decimation$keepRigTarget(boolean setViewport, CallbackInfo callback) {
        Framebuffer target = FirstPersonRigLayer.activeTarget();
        if (target != null && target != (Object) this) {
            target.beginWrite(setViewport);
            callback.cancel();
        }
    }
}
