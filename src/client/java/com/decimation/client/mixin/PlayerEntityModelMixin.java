package com.decimation.client.mixin;

import com.decimation.client.firstperson.FirstPersonRenderState;
import com.decimation.client.gun.ClientWeaponController;
import com.decimation.module.gun.WeaponItem;
import com.decimation.module.gun.data.ArmRotation;
import com.decimation.module.gun.data.WeaponArmPose;
import com.decimation.module.gun.data.WeaponPresentation;
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
    @Inject(method = "setAngles", at = @At("TAIL"))
    private void decimation$applyFirstPersonWeaponPose(LivingEntity entity,
                                                        float limbAngle, float limbDistance,
                                                        float animationProgress, float headYaw,
                                                        float headPitch, CallbackInfo callback) {
        if (!FirstPersonRenderState.isRenderingBody()
            || !(entity.getMainHandStack().getItem() instanceof WeaponItem weapon)) return;

        PlayerEntityModel<?> model = (PlayerEntityModel<?>) (Object) this;
        WeaponPresentation presentation = weapon.definition().presentation();
        WeaponArmPose pose = WeaponArmPose.interpolate(
            presentation.firstPersonHipArms(), presentation.firstPersonAdsArms(),
            ClientWeaponController.INSTANCE.adsProgress());
        boolean rightHanded = entity.getMainArm() == Arm.RIGHT;
        if (rightHanded) {
            apply(model.rightArm, model, pose.mainHand(), false);
            apply(model.leftArm, model, pose.offHand(), false);
        } else {
            apply(model.leftArm, model, pose.mainHand(), true);
            apply(model.rightArm, model, pose.offHand(), true);
        }
    }

    private static void apply(ModelPart arm, PlayerEntityModel<?> model,
                              ArmRotation rotation, boolean mirror) {
        float yaw = mirror ? -rotation.yaw() : rotation.yaw();
        float roll = mirror ? -rotation.roll() : rotation.roll();
        arm.pitch = model.head.pitch + (float) Math.toRadians(rotation.pitch());
        arm.yaw = model.head.yaw + (float) Math.toRadians(yaw);
        arm.roll = (float) Math.toRadians(roll);
    }
}
