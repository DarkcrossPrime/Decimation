package com.decimation.client.firstperson;

import com.decimation.client.gun.ClientWeaponPresentation;
import com.decimation.client.gun.WeaponVisualData;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;
import com.mojang.blaze3d.vertex.PoseStack;
import com.decimation.client.gun.WeaponSpecialRenderer;
import org.joml.Matrix4fc;

/** Retarget the authored OffHand timeline onto a straight, shoulder-anchored arm. */
public final class ReloadSupportArm {
    private static final float MOTION_SCALE = .2f;
    private ReloadSupportArm() { }

    /** Two player-model pixels lower, half a pixel right, only during the hand action. */
    public static void offset(Vector3f target, WeaponVisualData data, ClientWeaponPresentation.Sample sample, Matrix4fc gunPose, boolean left) {
        if (sample.kind() != ClientWeaponPresentation.Kind.RELOAD) return;
        var track = data.reload().tracks().get("OffHand");
        if (track == null) return;
        var motion = track.sample(sample.frame());var rest = track.sample(0);
        float weight = Math.clamp(Math.max(Math.abs(motion.pitch() - rest.pitch()), Math.abs(motion.roll() - rest.roll())) / 35, 0, 1);
        weight = weight * weight * (3 - 2 * weight);
        if (weight == 0) return;
        float heightScale = gunPose.transformDirection(new Vector3f(0, 1, 0)).length();
        float sideScale = gunPose.transformDirection(new Vector3f(0, 0, 1)).length();
        if (heightScale > .00001f && sideScale > .00001f) {
            target.y -= 2 * weight / (16 * heightScale);
            target.z += (left ? -.5f : .5f) * weight / (16 * sideScale);
        }
    }

    /** Blend from the tuned barrel grip to the animated magazine during the authored hand action. */
    public static Vector3f target(WeaponVisualData data, ClientWeaponPresentation.Sample sample, Vector3f idle) {
        if (sample.kind() != ClientWeaponPresentation.Kind.RELOAD || data.reloadGrip() == null) return idle;
        var hand = data.reload().tracks().get("OffHand");
        if (hand == null) return idle;
        var motion = hand.sample(sample.frame());var rest = hand.sample(0);
        float weight = Math.clamp(Math.max(Math.abs(motion.pitch() - rest.pitch()), Math.abs(motion.roll() - rest.roll())) / 35, 0, 1);
        if (weight == 0) return idle;
        weight = weight * weight * (3 - 2 * weight);
        var grip = data.reloadGrip();var mag = new Vector3f(grip.x(), grip.y(), grip.z());
        var track = data.reload().track(data.definition().id().equals("decimation:famas_custom") ? "magazine" : "ammoModel0");
        var part = track.sample(sample.frame());
        // Archived crossbow frames hide a bolt thousands of model units away; never chase that sentinel.
        if (Math.abs(part.x()) < 128 && Math.abs(part.y()) < 128 && Math.abs(part.z()) < 128) {
            var pose = new PoseStack();WeaponSpecialRenderer.applyReloadPart(pose, track, sample.frame());
            pose.last().pose().transformPosition(mag);
        }
        return idle.lerp(mag, weight);
    }

    public static void apply(ModelPart arm, WeaponVisualData data, ClientWeaponPresentation.Sample sample, boolean leftMainHand) {
        if (sample.kind() != ClientWeaponPresentation.Kind.RELOAD) return;
        var track = data.reload().tracks().get("OffHand");
        if (track == null) return;
        var motion = track.sample(sample.frame());
        var rest = track.sample(0);
        float side = leftMainHand ? -1 : 1;
        // ANIB's hand positions are player-model pixels (Y-down), unlike OBJ component
        // transforms. Subtract the authored rest pose so frame zero retains our tuned grip.
        float x = side * (motion.x() - rest.x()) * MOTION_SCALE / 16;
        float y = (track.modelYDown() ? 1 : -1) * (motion.y() - rest.y()) * MOTION_SCALE / 16;
        float z = (motion.z() - rest.z()) * MOTION_SCALE / 16;
        float pitch = MOTION_SCALE * radians(motion.pitch() - rest.pitch());
        float yaw = side * MOTION_SCALE * radians(motion.yaw() - rest.yaw());
        float roll = side * MOTION_SCALE * radians(motion.roll() - rest.roll());
        if (x == 0 && y == 0 && z == 0 && pitch == 0 && yaw == 0 && roll == 0) return;
        // Apply the hand delta to the current reach vector, never to the shoulder pivot.
        // Rotating toward the result preserves limb length in F5 and first person.
        var reach = new Vector3f(0, 10 * arm.yScale / 16, 0)
            .rotateX(arm.xRot).rotateY(arm.yRot).rotateZ(arm.zRot)
            .rotateX(pitch).rotateY(yaw).rotateZ(roll).add(x, y, z);
        var pose = FirstPersonSupportArmSolver.pointAt(reach.x, reach.y, reach.z);
        if (pose != null) { arm.xRot = pose.pitch();arm.yRot = pose.yaw();arm.zRot = 0; }
    }
    private static float radians(float degrees) { return (float) Math.toRadians(degrees); }
}
