package com.decimation.client.mixin;

import com.decimation.client.firstperson.FirstPersonRenderState;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the rig parent before each part's existing translation, rotation and scale. */
@Mixin(ModelPart.class)
public abstract class ModelPartMixin {
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V",
        at = @At("HEAD"), cancellable = true)
    private void decimation$selectFirstPersonPass(MatrixStack matrices, VertexConsumer vertices,
                                                  int light, int overlay, float red, float green,
                                                  float blue, float alpha, CallbackInfo callback) {
        if (!FirstPersonRenderState.shouldRenderPart((ModelPart) (Object) this)) callback.cancel();
    }

    @Inject(method = "rotate(Lnet/minecraft/client/util/math/MatrixStack;)V", at = @At("HEAD"))
    private void decimation$applyRigParent(MatrixStack matrices, CallbackInfo callback) {
        FirstPersonRenderState.applyRigParent((ModelPart) (Object) this, matrices);
    }

    @Inject(method = "copyTransform", at = @At("TAIL"))
    private void decimation$copyRigParent(ModelPart source, CallbackInfo callback) {
        FirstPersonRenderState.copyRigParent(source, (ModelPart) (Object) this);
    }
}
