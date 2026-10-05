package com.decimation.client.gun;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Tick-filtered inputs; bounded periodic breathing/walking, sampled during extraction only. */
public final class WeaponAmbientMotion {
    private float movement, previousMovement, lagYaw, previousYaw, lagPitch, previousPitch;
    public void tick(float speed, float yawDelta, float pitchDelta) {
        previousMovement = movement;previousYaw = lagYaw;previousPitch = lagPitch;
        movement += (finiteClamp(speed * 4, 0, 1) - movement) * .3f;
        lagYaw += (finiteClamp(yawDelta, -8, 8) - lagYaw) * .25f;
        lagPitch += (finiteClamp(pitchDelta, -8, 8) - lagPitch) * .25f;
    }
    public Pose sample(double time, float delta, float aim, float suppression) {
        return sample(time, delta, aim, suppression, 1);
    }
    public Pose sample(double time, float delta, float aim, float suppression, float tracking) {
        return wave(time, lerp(previousMovement, movement, delta), lerp(previousYaw, lagYaw, delta) * tracking,
            lerp(previousPitch, lagPitch, delta) * tracking, aim, suppression);
    }
    public static Pose wave(double time, float movement, float yawLag, float pitchLag, float aim, float suppression) {
        float gain = (1 - .8f * finiteClamp(aim, 0, 1)) * (1 - finiteClamp(suppression, 0, 1));
        if (gain == 0) return Pose.NONE;
        double breath = (time % 88) * (Math.PI * 2 / 88), step = (time % 20) * (Math.PI * 2 / 20);
        float moving = finiteClamp(movement, 0, 1);
        return new Pose((float) (.22 * Math.sin(breath) + .35 * moving * Math.sin(2 * step) + .08 * pitchLag) * gain,
            (float) (.13 * Math.sin(breath * 2) - .10 * yawLag) * gain,
            (float) (.7 * moving * Math.sin(step) - .08 * yawLag) * gain,
            (float) (.003 * Math.sin(breath) + .008 * moving * Math.cos(2 * step)) * gain);
    }
    public static Matrix4f cameraTransform(Pose pose, Vector3fc forward, Vector3fc up) {
        var right = new Vector3f(forward).cross(up).normalize();
        return new Matrix4f().translate(new Vector3f(up).mul(pose.height()))
            .rotate((float) Math.toRadians(pose.yaw()), up)
            .rotate((float) Math.toRadians(pose.pitch()), right)
            .rotate((float) Math.toRadians(pose.roll()), forward);
    }
    public void reset() { movement = previousMovement = lagYaw = previousYaw = lagPitch = previousPitch = 0; }
    private static float finiteClamp(float value, float min, float max) { return Float.isFinite(value) ? Math.clamp(value, min, max) : 0; }
    private static float lerp(float a, float b, float delta) { return a + (b - a) * delta; }
    public record Pose(float pitch, float yaw, float roll, float height) {
        public static final Pose NONE = new Pose(0, 0, 0, 0);
    }
}
