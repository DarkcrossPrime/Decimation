package com.decimation.client.mixin;

import com.decimation.client.firstperson.FirstPersonRenderState;
import com.decimation.module.gun.WeaponItem;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Inject(method = "setModelPose", at = @At("TAIL"))
    private void decimation$prepareFirstPersonBody(AbstractClientPlayerEntity player,
                                                    CallbackInfo callback) {
        if (!FirstPersonRenderState.isRenderingPlayer(player)) return;

        PlayerEntityModel<AbstractClientPlayerEntity> model =
            ((PlayerEntityRenderer) (Object) this).getModel();
        model.head.visible = false;
        model.hat.visible = false;

        boolean rightHanded = player.getMainArm() == Arm.RIGHT;
        FirstPersonRenderState.registerPlayerParts(model, rightHanded);
        if (!(player.getMainHandStack().getItem() instanceof WeaponItem)) return;
        model.rightArmPose = rightHanded
            ? BipedEntityModel.ArmPose.CROSSBOW_HOLD : BipedEntityModel.ArmPose.EMPTY;
        model.leftArmPose = rightHanded
            ? BipedEntityModel.ArmPose.EMPTY : BipedEntityModel.ArmPose.CROSSBOW_HOLD;
    }
}
