package com.decimation.client.mixin;

import com.decimation.client.gun.DroppedWeaponAccess;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ItemEntityRenderState.class, remap = false)
public abstract class ItemEntityRenderStateMixin implements DroppedWeaponAccess {
    @Unique private boolean decimation$weapon;
    public boolean decimation$isWeapon() { return decimation$weapon; }
    public void decimation$setWeapon(boolean weapon) { decimation$weapon = weapon; }
}
