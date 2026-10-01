package com.decimation.client.mixin;

import com.decimation.client.firstperson.FirstPersonRenderState;
import com.decimation.client.gun.ClientWeaponController;
import com.decimation.module.gun.WeaponItem;
import com.decimation.module.gun.data.ArmRotation;
import com.decimation.module.gun.data.WeaponArmPose;
import com.decimation.module.gun.data.WeaponPresentation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.model.ModelPart;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {
    private static final float FIRST_PERSON_MAIN_ARM_PITCH_OFFSET = 8.0f;
    private static final float FIRST_PERSON_MAIN_ARM_YAW_OFFSET = 12.0f;
    private static final float EXTERNAL_SUPPORT_ARM_LOWER = 8.0f;
    private static final float EXTERNAL_SUPPORT_ARM_INWARD = 10.0f;
    private static final float EXTERNAL_LOOK_ROTATION_FACTOR = 0.75f;

    @Inject(method = "setAngles", at = @At("TAIL"))
    private void decimation$applyWeaponPose(LivingEntity entity,
                                            float limbAngle, float limbDistance,
                                            float animationProgress, float headYaw,
                                            float headPitch, CallbackInfo callback) {
        if (!(entity.getMainHandStack().getItem() instanceof WeaponItem weapon)) return;

        PlayerEntityModel<?> model = (PlayerEntityModel<?>) (Object) this;
        WeaponPresentation presentation = weapon.definition().presentation();
        boolean firstPerson = FirstPersonRenderState.isRenderingPlayer(entity);
        if (firstPerson) {
            FirstPersonRenderState.solveRig(headPitch);
            FirstPersonRenderState.rememberArm(model.rightArm);
            FirstPersonRenderState.rememberArm(model.leftArm);
        }
        float lookFactor = firstPerson ? FirstPersonRenderState.LOOK_ROTATION_FACTOR
            : EXTERNAL_LOOK_ROTATION_FACTOR;
        // The first-person parent supplies pitch once, before the arm rest pose.
        // External players keep the original third-person posing.
        float armLookPitch = firstPerson ? 0.0f : model.head.pitch * lookFactor;
        model.head.yaw *= lookFactor;
        model.head.pitch *= lookFactor;
        model.hat.copyTransform(model.head);
        WeaponArmPose pose = presentation.firstPersonHipArms();
        float hipWeight = 1.0f;
        if (entity == MinecraftClient.getInstance().player) {
            ClientWeaponController controller = ClientWeaponController.INSTANCE;
            float tickDelta = MinecraftClient.getInstance().getTickDelta();
            float ads = controller.adsProgress(tickDelta);
            float sprint = controller.sprintProgress(tickDelta);
            WeaponArmPose held = WeaponArmPose.interpolate(pose,
                presentation.firstPersonAdsArms(), ads);
            pose = WeaponArmPose.interpolate(held, presentation.firstPersonSprintArms(),
                sprint);
            hipWeight = (1.0f - ads) * (1.0f - sprint);
        }
        boolean rightHanded = entity.getMainArm() == Arm.RIGHT;
        ArmRotation support = pose.offHand();
        if (!firstPerson) {
            support = new ArmRotation(support.pitch() + EXTERNAL_SUPPORT_ARM_LOWER,
                support.yaw() + EXTERNAL_SUPPORT_ARM_INWARD, support.roll());
        }
        if (rightHanded) {
            apply(model.rightArm, model, pose.mainHand(), false, firstPerson, armLookPitch,
                FIRST_PERSON_MAIN_ARM_PITCH_OFFSET,
                FIRST_PERSON_MAIN_ARM_YAW_OFFSET * hipWeight);
            apply(model.leftArm, model, support, false, firstPerson, armLookPitch, 0.0f, 0.0f);
        } else {
            apply(model.leftArm, model, pose.mainHand(), true, firstPerson, armLookPitch,
                FIRST_PERSON_MAIN_ARM_PITCH_OFFSET,
                FIRST_PERSON_MAIN_ARM_YAW_OFFSET * hipWeight);
            apply(model.rightArm, model, support, true, firstPerson, armLookPitch, 0.0f, 0.0f);
        }

        if (firstPerson) {
            ModelPart firingArm = rightHanded ? model.rightArm : model.leftArm;
            ModelPart supportArm = rightHanded ? model.leftArm : model.rightArm;
            // The support pass aims this arm at the current rendered gun grip.
            // Keep a neutral rest pose until that target is available.
            supportArm.pitch = 0.0f;
            supportArm.yaw = 0.0f;
            supportArm.roll = 0.0f;
            FirstPersonRenderState.prepareWeaponRig(model, firingArm, supportArm, hipWeight);
            // Recoil remains local to the firing arm, after the shoulder parent.
            ClientWeaponController controller = ClientWeaponController.INSTANCE;
            firingArm.pitch -= (float) Math.toRadians(controller.recoilPitch());
            firingArm.yaw += (float) Math.toRadians(controller.recoilYaw());
        }

        // Vanilla copies the arm transforms to the skin sleeves inside setAngles.
        // Our weapon pose runs at TAIL, after that copy, so repeat it with the final
        // arm transforms. Otherwise the sleeves remain in the vanilla pose and look
        // like a second pair of arms. Chest armor copies these same final arm
        // transforms from the player model when its feature renderer runs.
        model.rightSleeve.copyTransform(model.rightArm);
        model.leftSleeve.copyTransform(model.leftArm);
        if (firstPerson) FirstPersonRenderState.shrinkArms(model);
    }

    private static void apply(ModelPart arm, PlayerEntityModel<?> model,
                              ArmRotation rotation, boolean mirror, boolean firstPerson,
                              float armLookPitch,
                              float firstPersonPitchOffset, float firstPersonYawOffset) {
        float yaw = (mirror ? -1.0f : 1.0f)
            * (rotation.yaw() + (firstPerson ? firstPersonYawOffset : 0.0f));
        float roll = mirror ? -rotation.roll() : rotation.roll();
        float pitch = armLookPitch + (float) Math.toRadians(
            rotation.pitch() + (firstPerson ? firstPersonPitchOffset : 0.0f));
        arm.pitch = pitch;
        arm.yaw = model.head.yaw + (float) Math.toRadians(yaw);
        arm.roll = (float) Math.toRadians(roll);
    }
}
