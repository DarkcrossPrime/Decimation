package com.decimation.module.gun.data;

import java.util.Objects;

/** Main-hand and support-hand rotations for one first-person weapon pose. */
public record WeaponArmPose(ArmRotation mainHand, ArmRotation offHand) {
    public WeaponArmPose {
        Objects.requireNonNull(mainHand, "mainHand");
        Objects.requireNonNull(offHand, "offHand");
    }

    public static WeaponArmPose interpolate(WeaponArmPose from, WeaponArmPose to, float delta) {
        return new WeaponArmPose(
            ArmRotation.interpolate(from.mainHand, to.mainHand, delta),
            ArmRotation.interpolate(from.offHand, to.offHand, delta));
    }
}
