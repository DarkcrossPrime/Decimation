package com.decimation.client.firstperson;

import com.decimation.client.gun.WeaponMotion;
import com.decimation.client.gun.WeaponItemModel;
import com.decimation.client.gun.WeaponSprintPose;
import com.decimation.client.gun.WeaponRelaxedPose;
import com.decimation.client.gun.WeaponViewTransforms;
import com.decimation.client.gun.WeaponSpecialRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import com.decimation.module.gun.WeaponItem;
import com.decimation.module.gun.data.ArmRotation;
import com.decimation.module.gun.data.WeaponArmPose;
import com.decimation.module.gun.data.WeaponPresentation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Pure pose operations shared by extraction and the external player model. */
public final class WeaponPlayerPose {
    private WeaponPlayerPose() { }
    public static WeaponArmPose interpolate(WeaponPresentation p, WeaponMotion.Snapshot motion) {
        var held = WeaponArmPose.interpolate(p.firstPersonHipArms(), p.firstPersonAdsArms(), motion.aim());
        if (motion.relaxed() > 0) held = WeaponArmPose.interpolate(held, WeaponRelaxedPose.arms(p), motion.relaxed());
        return motion.sprintCarry() == 0 ? held : WeaponArmPose.interpolate(held, WeaponSprintPose.arms(p), motion.sprintCarry());
    }
    public static void external(PlayerModel model, AvatarRenderState state, WeaponMotion.Snapshot motion) {
        if (!(state.getMainHandItemStack().getItem() instanceof WeaponItem weapon)) return;
        external(model, state.mainArm, weapon.definition().presentation(), motion);
        var sample = ((WeaponPoseAccess) state).decimation$getAnimation();
        boolean reloading = sample.kind() == com.decimation.client.gun.ClientWeaponPresentation.Kind.RELOAD;
        if (!reloading && motion.relaxed() <= 0) return;
        var binding = Minecraft.getInstance().getModelManager().getItemModel(Identifier.parse(weapon.definition().id()));
        if (binding instanceof WeaponItemModel item) {
            boolean emptyOffhand = (state.mainArm == HumanoidArm.RIGHT ? state.leftHandItemStack : state.rightHandItemStack).isEmpty();
            if (reloading) reload(model, state.mainArm, item.data(), sample, emptyOffhand);
            else if (emptyOffhand) restSupport(model, state.mainArm, item.data(), motion);
            sleeves(model);
        }
    }

    /** Seek the real barrel grip after rest counter-rotation; preserve shoulder and limb length. */
    public static void restSupport(PlayerModel model, HumanoidArm main, com.decimation.client.gun.WeaponVisualData data, WeaponMotion.Snapshot motion) {
        var target = restSupportTarget(model, main, data, motion);
        if (target == null) return;
        var support = model.getArm(main.getOpposite());
        var pose = FirstPersonSupportArmSolver.pointAt(target.x * 16 - support.x, target.y * 16 - support.y, target.z * 16 - support.z);
        if (pose != null) {
            support.xRot += (pose.pitch() - support.xRot) * motion.relaxed();
            float yawDelta = (float) Math.atan2(Math.sin(pose.yaw() - support.yRot), Math.cos(pose.yaw() - support.yRot));
            support.yRot += yawDelta * motion.relaxed();support.zRot *= 1 - motion.relaxed();
        }
    }

    public static Vector3f restSupportTarget(PlayerModel model, HumanoidArm main, com.decimation.client.gun.WeaponVisualData data, WeaponMotion.Snapshot motion) {
        if (data.supportGrip() == null) return null;
        boolean left = main == HumanoidArm.LEFT;
        var hand = new PoseStack();model.translateToHand(null, main, hand);
        hand.rotateDegrees(Axis.XP, -90);hand.rotateDegrees(Axis.YP, 180);
        hand.translate((left ? -1f : 1f) / 16, 2f / 16, -10f / 16);hand.translate(-.5f, -.5f, -.5f);
        hand.mulPose(WeaponViewTransforms.create(data.definition().presentation(), data.mesh().bounds(),
            left ? net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND : net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, motion));
        var grip = data.supportGrip();float size = data.definition().presentation().thirdPerson().scale() * 2.5f;
        var local = new Vector3f(grip.x() + 1 / (16 * size), grip.y() - 2 / (16 * size), grip.z() + (left ? -.4f : .4f) / (16 * size));
        var root = new PoseStack();model.root().translateAndRotate(root);
        var mount = new Matrix4f(root.last().pose()).invert().mul(hand.last().pose());
        var target = mount.transformPosition(new Vector3f(local));
        var direction = mount.transformDirection(new Vector3f(1, 0, 0)).normalize();
        var support = model.getArm(main.getOpposite());
        var shoulder = new Vector3f(support.x, support.y, support.z).div(16);
        var offset = new Vector3f(target).sub(shoulder);
        float lower = mount.transformPosition(new Vector3f(Math.max(0, data.mesh().bounds().minX()), local.y, local.z)).sub(target).dot(direction);
        float upper = mount.transformPosition(new Vector3f(data.mesh().bounds().maxX(), local.y, local.z)).sub(target).dot(direction);
        float projection = offset.dot(direction), discriminant = projection * projection - offset.lengthSquared() + 100f / 256;
        // Intersect the barrel line with fixed ten-pixel wrist reach, then stay within the real barrel.
        if (discriminant >= 0) {
            float radius = (float) Math.sqrt(discriminant);
            float a = Math.clamp(-projection - radius, lower, upper), b = Math.clamp(-projection + radius, lower, upper);
            float errorA = Math.abs(new Vector3f(offset).fma(a, direction).lengthSquared() - 100f / 256);
            float errorB = Math.abs(new Vector3f(offset).fma(b, direction).lengthSquared() - 100f / 256);
            target.fma(errorA < errorB || errorA == errorB && Math.abs(a) < Math.abs(b) ? a : b, direction);
        }
        return target;
    }

    public static void reload(PlayerModel model, HumanoidArm main, com.decimation.client.gun.WeaponVisualData data,
                              com.decimation.client.gun.ClientWeaponPresentation.Sample sample, boolean emptyOffhand) {
        boolean left = main == HumanoidArm.LEFT;
        ReloadFiringArm.apply(model.getArm(main), data, sample, left, false);
        if (!emptyOffhand) return;
        seekFiringHand(model, main);
        var hand = new PoseStack();model.translateToHand(null, main, hand);
        hand.rotateDegrees(Axis.XP, -90);hand.rotateDegrees(Axis.YP, 180);
        hand.translate((left ? -1f : 1f) / 16, 2f / 16, -10f / 16);
        hand.translate(-.5f, -.5f, -.5f);
        hand.mulPose(WeaponViewTransforms.create(data.definition().presentation(), data.mesh().bounds(),
            left ? net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND : net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, sample.motion()));
        WeaponSpecialRenderer.applyRoot(hand, data, sample, true);
        var root = new PoseStack();model.root().translateAndRotate(root);
        var idle = root.last().pose().transformPosition(supportTarget(model, main));
        new Matrix4f(hand.last().pose()).invert().transformPosition(idle);
        var target = ReloadSupportArm.target(data, sample, idle);
        ReloadSupportArm.offset(target, data, sample, hand.last().pose(), left);
        hand.last().pose().transformPosition(target);
        new Matrix4f(root.last().pose()).invert().transformPosition(target);
        var arm = model.getArm(main.getOpposite());
        var pose = FirstPersonSupportArmSolver.pointAt(target.x * 16 - arm.x, target.y * 16 - arm.y, target.z * 16 - arm.z);
        if (pose != null) { arm.xRot = pose.pitch();arm.yRot = pose.yaw();arm.zRot = 0; }
        ReloadSupportArm.apply(arm, data, sample, left);
    }

    public static void external(PlayerModel model, HumanoidArm mainArm, WeaponPresentation presentation, WeaponMotion.Snapshot motion) {
        var pose = interpolate(presentation, motion);
        float tracking = WeaponRigPolicy.tracking(motion.relaxed());
        float pitch = model.head.xRot * tracking, yaw = model.head.yRot * .75f * tracking;
        boolean mirror = mainArm == HumanoidArm.LEFT;
        // OBJ forward follows the arm: calibrate ready hold to two degrees below head pitch.
        apply(model.getArm(mainArm), pose.mainHand(), mirror, pitch + radians((-88 - pose.mainHand().pitch()) * (1 - motion.carry()) + motion.ambient().pitch()),
            yaw + radians(motion.ambient().yaw()), 0, 0);
        model.getArm(mainArm).zRot += radians(motion.ambient().roll());
        seekFiringHand(model, mainArm);
        sleeves(model);
    }

    /** Model-root-local point one model pixel ahead of the actual item-in-hand origin. */
    public static Vector3f supportTarget(PlayerModel model, HumanoidArm mainArm) {
        var hand = new PoseStack();
        model.translateToHand(null, mainArm, hand); // Includes vanilla's classic/slim wrist offset.
        hand.rotateDegrees(Axis.XP, -90);hand.rotateDegrees(Axis.YP, 180);
        hand.translate((mainArm == HumanoidArm.LEFT ? -1f : 1f) / 16, 2f / 16, -10f / 16);
        var target = hand.last().pose().transformPosition(new Vector3f(0, 0, -1f / 16));
        var root = new PoseStack();model.root().translateAndRotate(root);
        return new Matrix4f(root.last().pose()).invert().transformPosition(target);
    }

    /** Rotate toward the firing hand. Third-person shoulders and all limb scales stay unchanged. */
    public static void seekFiringHand(PlayerModel model, HumanoidArm mainArm) {
        var target = supportTarget(model, mainArm);
        var support = model.getArm(mainArm.getOpposite());
        var pose = FirstPersonSupportArmSolver.pointAt(target.x * 16 - support.x, target.y * 16 - support.y, target.z * 16 - support.z);
        if (pose != null) {
            support.xRot = pose.pitch();support.yRot = pose.yaw();support.zRot = 0;
        }
    }
    public static void apply(ModelPart arm, ArmRotation rotation, boolean mirror, float pitch, float yaw, float pitchOffset, float yawOffset) {
        arm.xRot = pitch + radians(rotation.pitch() + pitchOffset);
        arm.yRot = yaw + radians((mirror ? -1 : 1) * (rotation.yaw() + yawOffset));
        arm.zRot = radians((mirror ? -1 : 1) * rotation.roll());
    }
    public static void sleeves(PlayerModel model) {
        // 26.3 sleeves are children of their arms; they inherit the final limb pose.
        model.rightSleeve.resetPose();model.leftSleeve.resetPose();
    }
    private static float radians(float value) { return (float) Math.toRadians(value); }
}
