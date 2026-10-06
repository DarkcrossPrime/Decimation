package com.decimation.client.firstperson;

import com.decimation.client.gun.WeaponAmbientMotion;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Relaxed arms follow the body; ready arms smoothly recover camera tracking. */
public final class WeaponRigPolicy {
    private WeaponRigPolicy() { }
    public static float tracking(float relaxed) { return 1 - Math.clamp(relaxed, 0, 1); }
    public static float yaw(float bodyYaw, float headYaw, float relaxed) { return bodyYaw + headYaw * .9f * tracking(relaxed); }
    public static float skinThickness(float aim) { return .68f * (1 - .5f * Math.clamp(aim, 0, 1)); }
    public static Matrix4f ambient(WeaponAmbientMotion.Pose motion, Matrix4fc body, Vector3fc cameraForward, Vector3fc cameraUp, float relaxed) {
        var forward = body.transformDirection(new Vector3f(0, 0, -1)).normalize();
        var up = body.transformDirection(new Vector3f(0, -1, 0)).normalize();
        var basis = new Quaternionf().lookAlong(forward, up).conjugate();
        var camera = new Quaternionf().lookAlong(cameraForward, cameraUp).conjugate();
        basis.slerp(camera, tracking(relaxed));
        return WeaponAmbientMotion.cameraTransform(motion, new Vector3f(0, 0, -1).rotate(basis), new Vector3f(0, 1, 0).rotate(basis));
    }
}
