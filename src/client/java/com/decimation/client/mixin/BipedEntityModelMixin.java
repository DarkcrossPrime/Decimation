package com.decimation.client.mixin;

import com.decimation.client.firstperson.FirstPersonRenderState;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelMixin {
    @Unique private ModelPart decimation$heldArm;
    @Unique private float decimation$heldXScale;
    @Unique private float decimation$heldYScale;
    @Unique private float decimation$heldZScale;

    @Inject(method = "copyBipedStateTo", at = @At("HEAD"))
    private void decimation$rememberArmorArms(BipedEntityModel<?> target, CallbackInfo callback) {
        BipedEntityModel<?> source = (BipedEntityModel<?>) (Object) this;
        if (!FirstPersonRenderState.isRenderingBody()
            || !FirstPersonRenderState.isShrunkArm(source.rightArm)) return;
        FirstPersonRenderState.rememberArm(target.rightArm);
        FirstPersonRenderState.rememberArm(target.leftArm);
    }

    @Inject(method = "copyBipedStateTo", at = @At("TAIL"))
    private void decimation$alignArmorArms(BipedEntityModel<?> target, CallbackInfo callback) {
        BipedEntityModel<?> source = (BipedEntityModel<?>) (Object) this;
        if (!FirstPersonRenderState.isRenderingBody()
            || !FirstPersonRenderState.isShrunkArm(source.rightArm)) return;
        copyArmTransform(source.rightArm, target.rightArm);
        copyArmTransform(source.leftArm, target.leftArm);
    }

    @Unique
    private static void copyArmTransform(ModelPart source, ModelPart target) {
        target.xScale = source.xScale;
        target.yScale = source.yScale;
        target.zScale = source.zScale;
        target.pivotX = source.pivotX;
        target.pivotY = source.pivotY;
        target.pivotZ = source.pivotZ;
    }

    @Inject(method = "setArmAngle", at = @At("HEAD"))
    private void decimation$keepWeaponGrip(Arm side, MatrixStack matrices,
                                           CallbackInfo callback) {
        if (!FirstPersonRenderState.isRenderingBody()) return;
        BipedEntityModel<?> model = (BipedEntityModel<?>) (Object) this;
        ModelPart arm = side == Arm.RIGHT ? model.rightArm : model.leftArm;
        if (!FirstPersonRenderState.isShrunkArm(arm)) return;

        decimation$heldArm = arm;
        decimation$heldXScale = arm.xScale;
        decimation$heldYScale = arm.yScale;
        decimation$heldZScale = arm.zScale;
        FirstPersonRenderState.unscaleForHeldItem(arm);
    }

    @Inject(method = "setArmAngle", at = @At("RETURN"))
    private void decimation$restoreArmSize(Arm side, MatrixStack matrices,
                                           CallbackInfo callback) {
        if (decimation$heldArm == null) return;
        decimation$heldArm.xScale = decimation$heldXScale;
        decimation$heldArm.yScale = decimation$heldYScale;
        decimation$heldArm.zScale = decimation$heldZScale;
        decimation$heldArm = null;
    }
}
