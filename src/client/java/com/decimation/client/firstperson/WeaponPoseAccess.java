package com.decimation.client.firstperson;

import com.decimation.client.gun.WeaponMotion;
import com.decimation.client.gun.ClientWeaponPresentation;

public interface WeaponPoseAccess {
    WeaponMotion.Snapshot decimation$getMotion();
    void decimation$setMotion(WeaponMotion.Snapshot motion);
    ClientWeaponPresentation.Sample decimation$getAnimation();
    void decimation$setAnimation(ClientWeaponPresentation.Sample animation);
}
