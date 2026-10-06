package com.decimation.client.mixin;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.palette.PalettedTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = EntityRenderDispatcher.class, remap = false)
public interface EquipmentRendererAccessor {
    @Accessor("equipmentAssets") EquipmentAssetManager decimation$equipment();
    @Accessor("palettedTextures") PalettedTextureManager decimation$palettes();
}
