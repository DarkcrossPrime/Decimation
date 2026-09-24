package com.decimation.module.gun.data;

/** A model transform expressed in the held-item attachment coordinate space. */
public record WeaponTransform(float x, float y, float z,
                              float pitch, float yaw, float roll,
                              float scale) {
    public WeaponTransform {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
            || !Float.isFinite(pitch) || !Float.isFinite(yaw) || !Float.isFinite(roll)
            || !Float.isFinite(scale) || scale <= 0) {
            throw new IllegalArgumentException("invalid weapon presentation transform");
        }
    }

    public static WeaponTransform interpolate(WeaponTransform from, WeaponTransform to, float delta) {
        float amount = Math.max(0, Math.min(1, delta));
        return new WeaponTransform(
            lerp(from.x, to.x, amount),
            lerp(from.y, to.y, amount),
            lerp(from.z, to.z, amount),
            lerp(from.pitch, to.pitch, amount),
            lerp(from.yaw, to.yaw, amount),
            lerp(from.roll, to.roll, amount),
            lerp(from.scale, to.scale, amount));
    }

    private static float lerp(float from, float to, float delta) {
        return from + (to - from) * delta;
    }
}
