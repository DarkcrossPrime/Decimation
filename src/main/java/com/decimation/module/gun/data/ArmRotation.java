package com.decimation.module.gun.data;

/** Euler rotation, in degrees, applied to one first-person player arm. */
public record ArmRotation(float pitch, float yaw, float roll) {
    public ArmRotation {
        if (!Float.isFinite(pitch) || !Float.isFinite(yaw) || !Float.isFinite(roll)) {
            throw new IllegalArgumentException("invalid weapon arm rotation");
        }
    }

    public static ArmRotation interpolate(ArmRotation from, ArmRotation to, float delta) {
        float amount = Math.max(0, Math.min(1, delta));
        return new ArmRotation(
            lerp(from.pitch, to.pitch, amount),
            lerp(from.yaw, to.yaw, amount),
            lerp(from.roll, to.roll, amount));
    }

    private static float lerp(float from, float to, float delta) {
        return from + (to - from) * delta;
    }
}
