package com.decimation.client.mixin;

import com.decimation.client.firstperson.WeaponPoseAccess;
import com.decimation.client.gun.WeaponMotion;
import com.decimation.client.gun.ClientWeaponPresentation;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = AvatarRenderState.class, remap = false)
public abstract class AvatarRenderStateMixin implements WeaponPoseAccess {
    @Unique private WeaponMotion.Snapshot decimation$motion = WeaponMotion.Snapshot.REST;
    @Unique private ClientWeaponPresentation.Sample decimation$animation = ClientWeaponPresentation.Sample.REST;
    public WeaponMotion.Snapshot decimation$getMotion() { return decimation$motion; }
    public void decimation$setMotion(WeaponMotion.Snapshot motion) { decimation$motion = motion; }
    public ClientWeaponPresentation.Sample decimation$getAnimation() { return decimation$animation; }
    public void decimation$setAnimation(ClientWeaponPresentation.Sample animation) { decimation$animation = animation; }
}
