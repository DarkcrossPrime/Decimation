package com.decimation.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = AvatarRenderer.class, remap = false)
public interface AvatarRendererAccessor {
    @Invoker("setupRotations") void decimation$rotations(AvatarRenderState state, PoseStack poses, float bodyYaw, float scale);
    @Invoker("scale") void decimation$scale(AvatarRenderState state, PoseStack poses);
}
