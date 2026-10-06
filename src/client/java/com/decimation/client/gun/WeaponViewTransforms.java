package com.decimation.client.gun;

import com.decimation.client.content.BakedObjMesh;
import com.decimation.module.gun.data.WeaponPresentation;
import com.decimation.module.gun.data.WeaponTransform;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;

/** Definition-owned transforms in item and posed-hand space. */
public final class WeaponViewTransforms {
    private WeaponViewTransforms() { }

    public static Matrix4f create(WeaponPresentation presentation, BakedObjMesh.Bounds bounds,
                                  ItemDisplayContext context, WeaponMotion.Snapshot motion) {
        Matrix4f matrix = new Matrix4f();
        if (context.firstPerson()) {
            WeaponTransform transform = firstPerson(presentation, motion);
            float hip = (1 - motion.aim()) * (1 - motion.carry());
            // Convert definition-owned hand attachment offsets to camera space. Keep canonical values untouched.
            matrix.translate(transform.x() - 0.5f * motion.aim() - 0.0175f * hip - .5f / 16,
                transform.y() - 0.6f - 0.5f / 16 * hip, -transform.z() - 0.2f - hip / 16);
            matrix.rotateX(radians(-motion.recoilPitch())).rotateY(radians(motion.recoilYaw()));
            rotate(matrix, transform, 3 * hip);
            float size = transform.scale() * 1.32f * (1 + 0.30f * hip);
            matrix.scale(size).scale(1 - 0.25f * hip, 1, 1);
            if (context.leftHand()) matrix = new Matrix4f().scale(-1, 1, 1).mul(matrix);
        } else if (context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
            WeaponTransform transform = presentation.thirdPerson();
            // The weapon player pose now raises the arm into the recovered model basis.
            matrix.translate(transform.x() - 0.5f - .5f / 16, transform.y() - 0.5f - 1.5f / 16, transform.z() - 0.5f);
            rotate(matrix, transform, 0);
            matrix.translate(-4f / 16, 0, 0); // Four model pixels backward along the barrel, before weapon scaling.
            matrix.scale(transform.scale() * 2.5f);
            if (context.leftHand()) matrix = new Matrix4f().scale(-1, 1, 1).mul(matrix);
            WeaponRelaxedPose.flatten(matrix, presentation, motion, context.leftHand(), false);
            // LayerRenderState applies ItemTransform.NO_TRANSFORM's (-.5,-.5,-.5)
            // even for special models. Cancel that once, outside left-hand mirroring.
            matrix = new Matrix4f().translate(.5f, .5f, .5f).mul(matrix);
        } else if (context == ItemDisplayContext.GROUND) {
            // Roll around the barrel, then center the two horizontal dimensions on the ground.
            matrix.rotateY(radians(90)).rotateX(radians(90)).scale(presentation.thirdPerson().scale() * 2.5f);
            matrix.translate(-(bounds.minX() + bounds.maxX()) / 2, -(bounds.minY() + bounds.maxY()) / 2, -bounds.maxZ());
        } else {
            // Center displays/drops using baked bounds, rather than the model's arbitrary recovered origin.
            matrix.rotateY(radians(90)).scale(presentation.thirdPerson().scale());
            float y = (bounds.minY() + bounds.maxY()) / 2;
            matrix.translate(-(bounds.minX() + bounds.maxX()) / 2, -y, -(bounds.minZ() + bounds.maxZ()) / 2);
        }
        return matrix;
    }

    /** Attachment after the firing arm and vanilla adult hand operations. Recoil belongs to the arm. */
    public static Matrix4f rig(WeaponPresentation presentation, WeaponMotion.Snapshot motion, boolean left) {
        var transform = firstPerson(presentation, motion);
        float hip = (1 - motion.aim()) * (1 - motion.carry());
        var matrix = new Matrix4f().translate(transform.x() - .5f - .0175f * hip - .5f / 16,
            transform.y() - .5f - .5f / 16 * hip - 1f / 16, transform.z() - .5f + 3f / 11 - hip / 16);
        rotate(matrix, transform, 3 * hip);
        matrix.scale(transform.scale() * 1.32f * (1 + .30f * hip)).scale(1 - .25f * hip, 1, 1);
        if (left) matrix = new Matrix4f().scale(-1, 1, 1).mul(matrix);
        return WeaponRelaxedPose.flatten(matrix, presentation, motion, left, true);
    }

    public static WeaponTransform firstPerson(WeaponPresentation p, WeaponMotion.Snapshot motion) {
        var held = WeaponTransform.interpolate(p.firstPersonHip(), p.firstPersonAds(), motion.aim());
        if (motion.relaxed() > 0) held = WeaponTransform.interpolate(held, WeaponRelaxedPose.weapon(p), motion.relaxed());
        return motion.sprintCarry() == 0 ? held : WeaponTransform.interpolate(held, WeaponSprintPose.weapon(p), motion.sprintCarry());
    }

    private static void rotate(Matrix4f matrix, WeaponTransform transform, float pitchOffset) {
        matrix.rotateX(radians(transform.pitch() + pitchOffset)).rotateY(radians(transform.yaw()))
            .rotateZ(radians(transform.roll())).rotateY(radians(90)); // Recovered OBJ +X is muzzle-forward.
    }
    private static float radians(float degrees) { return (float) Math.toRadians(degrees); }
}
