package com.decimation.client.firstperson;

import com.decimation.client.gun.ClientWeaponPresentation;
import com.decimation.client.gun.WeaponSpecialRenderer;
import com.decimation.client.gun.WeaponViewTransforms;
import com.decimation.client.gun.WeaponVisualData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Put reload tilt into the firing arm once, so the weapon stays in its hand. */
public final class ReloadFiringArm {
    private ReloadFiringArm() { }
    public static boolean apply(ModelPart arm, WeaponVisualData data, ClientWeaponPresentation.Sample sample, boolean left, boolean firstPerson) {
        if (sample.kind() != ClientWeaponPresentation.Kind.RELOAD) return false;
        var root = new PoseStack();WeaponSpecialRenderer.applyRoot(root, data, sample);
        var attachment = new Matrix4f().rotateX(-(float) Math.PI / 2).rotateY((float) Math.PI);
        attachment.mul(firstPerson ? WeaponViewTransforms.rig(data.definition().presentation(), sample.motion(), left)
            : WeaponViewTransforms.create(data.definition().presentation(), data.mesh().bounds(),
                left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, sample.motion()));
        // Remove display scale before changing a limb orientation, retaining left-hand reflection.
        var basis = new Matrix3f(attachment);
        var column = new Vector3f();
        for (int i = 0; i < 3; i++) basis.setColumn(i, basis.getColumn(i, column).normalize());
        var inverse = new Matrix3f(basis).invert();
        var turn = basis.mul(new Matrix3f(root.last().pose())).mul(inverse);
        var euler = new Matrix3f().rotationZYX(arm.zRot, arm.yRot, arm.xRot).mul(turn).getEulerAnglesZYX(new Vector3f());
        arm.xRot = euler.x;arm.yRot = euler.y;arm.zRot = euler.z;
        return true;
    }
}
