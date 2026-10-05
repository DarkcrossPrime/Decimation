package com.decimation.client.gun;

import com.decimation.module.gun.data.ArmRotation;
import com.decimation.module.gun.data.WeaponArmPose;
import com.decimation.module.gun.data.WeaponPresentation;
import com.decimation.module.gun.data.WeaponTransform;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Body-relative relaxed hold, with the barrel across the chest and forward of the shoulder. */
public final class WeaponRelaxedPose {
    private WeaponRelaxedPose() { }
    public static WeaponArmPose arms(WeaponPresentation p) {
        return new WeaponArmPose(new ArmRotation(-55, -35, -15), p.firstPersonHipArms().offHand());
    }
    public static WeaponTransform weapon(WeaponPresentation p) {
        var hip = p.firstPersonHip();
        return new WeaponTransform(hip.x(), hip.y() + .025f, hip.z() + .02f, hip.pitch(), hip.yaw(), hip.roll(), hip.scale());
    }

    /** Counter the wrist angle at rest: OBJ +X lies across the torso and +Y stays upright. */
    public static Matrix4f flatten(Matrix4f mount, WeaponPresentation p, WeaponMotion.Snapshot motion, boolean left, boolean firstPerson) {
        if (motion.relaxed() <= 0) return mount;
        var arm = com.decimation.client.firstperson.WeaponPlayerPose.interpolate(p, motion).mainHand();
        float mirror = left ? -1 : 1;
        var wrist = new Quaternionf().rotationZYX(radians(arm.roll() * mirror), radians(arm.yaw() * mirror),
            radians(arm.pitch() + (firstPerson ? 8 : 0))).rotateX(radians(-90)).rotateY(radians(180));
        var target = wrist.invert().mul(new Quaternionf().rotationX(radians(180)));
        var size = mount.getScale(new Vector3f());
        var position = mount.getTranslation(new Vector3f());
        var rotation = new Matrix4f(mount).scale(1 / size.x, 1 / size.y, 1 / size.z)
            .scale(mirror, 1, 1).getNormalizedRotation(new Quaternionf());
        rotation.slerp(target, motion.relaxed());
        mount.translationRotateScale(position, rotation, size).scale(mirror, 1, 1);
        return mount;
    }
    private static float radians(float degrees) { return (float) Math.toRadians(degrees); }
}
