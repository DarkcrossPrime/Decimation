package com.decimation.client.mixin;

import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.StagedVertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = FeatureRenderDispatcher.class, remap = false)
public interface FeatureDispatcherAccessor {
    @Mutable @Accessor("stagedVertexBuffer") void decimation$buffer(StagedVertexBuffer buffer);
}
