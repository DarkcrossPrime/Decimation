package com.decimation.client.firstperson;

import com.decimation.client.gun.WeaponSightLine;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Rigid, camera-relative alignment. Move the arm parent and gun together, never the body. */
public final class WeaponAdsAlignment {
    private WeaponAdsAlignment() { }
    public static Matrix4f solve(Matrix4fc neutralGun, WeaponSightLine sight, Vector3fc forward, Vector3fc up, float progress) {
        float amount = Float.isFinite(progress) ? Math.clamp(progress, 0, 1) : 0;
        if (amount == 0 || sight == null) return new Matrix4f();
        var rear = neutralGun.transformPosition(sight.rear().vector());
        var direction = neutralGun.transformPosition(sight.front().vector()).sub(rear).normalize();
        var localUp = neutralGun.transformDirection(new Vector3f(0, 1, 0));
        var source = basis(direction, localUp);
        var cameraForward = new Vector3f(forward).normalize();
        var target = basis(cameraForward, up);
        var delta = target.mul(source.transpose());
        var turn = new Quaternionf().slerp(delta.getNormalizedRotation(new Quaternionf()), amount);
        var destination = new Vector3f(rear).lerp(cameraForward.mul(sight.eyeRelief()), amount);
        return new Matrix4f().translate(destination).rotate(turn).translate(-rear.x, -rear.y, -rear.z);
    }
    private static Matrix3f basis(Vector3fc forward, Vector3fc up) {
        var right = new Vector3f(forward).cross(up).normalize();
        var orthogonalUp = new Vector3f(right).cross(forward).normalize();
        return new Matrix3f().setColumn(0, right).setColumn(1, orthogonalUp).setColumn(2, new Vector3f(forward).negate());
    }
}
