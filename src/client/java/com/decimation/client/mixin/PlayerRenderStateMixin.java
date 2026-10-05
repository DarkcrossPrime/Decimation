package com.decimation.client.mixin;

import com.decimation.client.firstperson.FirstPersonBodyRenderer;
import com.decimation.client.firstperson.FirstPersonFrameAccess;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PlayerRenderState.class, remap = false)
public abstract class PlayerRenderStateMixin implements FirstPersonFrameAccess {
    @Unique private FirstPersonBodyRenderer.Frame decimation$frame;
    public FirstPersonBodyRenderer.Frame decimation$getFrame() { return decimation$frame; }
    public void decimation$setFrame(FirstPersonBodyRenderer.Frame frame) { decimation$frame = frame; }
    @Inject(method = "reset", at = @At("HEAD"))
    private void decimation$reset(CallbackInfo ci) { decimation$frame = null; }
}
