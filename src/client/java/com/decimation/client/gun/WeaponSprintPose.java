package com.decimation.client.gun;

import com.decimation.module.gun.data.ArmRotation;
import com.decimation.module.gun.data.WeaponArmPose;
import com.decimation.module.gun.data.WeaponPresentation;
import com.decimation.module.gun.data.WeaponTransform;

/** Runtime carry pose; canonical definitions retain their archived sprint transforms. */
public final class WeaponSprintPose {
    private WeaponSprintPose() { }
    public static WeaponTransform weapon(WeaponPresentation p) {
        var hip = p.firstPersonHip();
        return new WeaponTransform(hip.x(), hip.y() + .04f, hip.z() - .04f, hip.pitch(), hip.yaw(), hip.roll(), hip.scale());
    }
    public static WeaponArmPose arms(WeaponPresentation p) {
        var hip = p.firstPersonHipArms();var main = hip.mainHand();
        return new WeaponArmPose(new ArmRotation(main.pitch() + 23, main.yaw() - 50, main.roll() - 8), hip.offHand());
    }
}
