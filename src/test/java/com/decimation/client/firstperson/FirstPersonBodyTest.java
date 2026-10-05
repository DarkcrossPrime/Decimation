package com.decimation.client.firstperson;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Vector3f;

/** Real cuboid emission, nested clothing and pose reuse, without a graphics device. */
public final class FirstPersonBodyTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        transparentRigBackground();
        bodyAnchorStability();
        for (boolean slim : new boolean[] {false, true}) {
            for (HumanoidArm side : HumanoidArm.values()) {
                for (float pitch : new float[] {-90, -65, -30, 0, 30, 65, 90}) {
                    var player = new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot(), slim);
                    var limb = player.getArm(side);
                    limb.xRot = (float) Math.toRadians(pitch);limb.yRot = .18f;limb.zRot = -.1f;
                    limb.xScale = limb.zScale = .68f;limb.yScale = 1.7f;
                    var parent = new PoseStack();
                    parent.translate(.3, -1.62, -.1875);parent.rotateDegrees(Axis.YP, 155);
                    parent.scale(-.9375f, -.9375f, .9375f);parent.rotateDegrees(Axis.XP, pitch);
                    var reference = emit(limb, parent);
                    var captured = new FrozenModel.Builder().add(parent, limb).build();
                    var actual = emit(captured.root(), new PoseStack());
                    equivalent(reference, actual);
                    check(actual.size() == 48, "skin and nested sleeve each appear once");
                    parent.translate(99, 99, 99);limb.x = 999;limb.xRot = 99;limb.visible = false;
                    captured.setupAnim(null);
                    equivalent(actual, emit(captured.root(), new PoseStack()));
                }
                var player = new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot(), slim);
                player.body.xRot = .4f;player.rightLeg.xRot = .7f;
                player.jacket.visible = false;player.leftPants.visible = false;
                var body = new FrozenModel.Builder().add(new PoseStack(), player.body).add(new PoseStack(), player.leftLeg).add(new PoseStack(), player.rightLeg).build();
                check(emit(body.root(), new PoseStack()).size() == 96, "body excludes head, arms and hidden clothing");
                player.getArm(side).xRot = -1.7f;
                WeaponPlayerPose.sleeves(player);
                var sleeve = side == HumanoidArm.RIGHT ? player.rightSleeve : player.leftSleeve;
                check(sleeve.storePose().equals(sleeve.getInitialPose()), "child sleeves retain local rest pose");
                player.getArm(side).skipDraw = true;
                var sleeveOnly = new FrozenModel.Builder().add(new PoseStack(), player.getArm(side)).build();
                check(emit(sleeveOnly.root(), new PoseStack()).size() == 24, "skipDraw hides parent cube while preserving sleeve child");
            }
        }
        System.out.println("First-person body checks passed: " + checks + " assertions; actual cuboid poses, classic/slim, both arms, clothing visibility and deferred reuse.");
    }
    private static void bodyAnchorStability() throws Exception {
        var avatar = new net.minecraft.client.renderer.entity.state.AvatarRenderState();
        avatar.x = 10;avatar.y = 65;avatar.z = -8;avatar.scale = 1;avatar.bodyRot = 37;
        var camera = new net.minecraft.world.phys.Vec3(10.1, 66.62, -7.88);
        var transforms = new com.decimation.client.mixin.AvatarRendererAccessor() {
            public void decimation$rotations(net.minecraft.client.renderer.entity.state.AvatarRenderState state,
                                             PoseStack poses, float yaw, float scale) { poses.rotateDegrees(Axis.YP, 180 - yaw); }
            public void decimation$scale(net.minecraft.client.renderer.entity.state.AvatarRenderState state, PoseStack poses) {
                poses.scale(.9375f, .9375f, .9375f);
            }
        };
        var catalog = com.decimation.module.gun.data.WeaponCatalog.load(FirstPersonBodyTest.class.getClassLoader());
        for (boolean narrow : new boolean[] {false, true}) for (var main : HumanoidArm.values()) {
            for (boolean crouch : new boolean[] {false, true}) {
                var offset = new net.minecraft.world.phys.Vec3(0, crouch ? -.125 : 0, 0);
                avatar.yRot = avatar.xRot = 0;
                var referenceBase = FirstPersonBodyRenderer.bodyBase(transforms, avatar, camera, offset);
                for (var weapon : catalog.definitions().values()) {
                    for (int view : new int[] {-1, 0, 1}) for (var motion : List.of(
                        com.decimation.client.gun.WeaponMotion.Snapshot.REST,
                        new com.decimation.client.gun.WeaponMotion.Snapshot(1, 0, 0, 0),
                        new com.decimation.client.gun.WeaponMotion.Snapshot(0, 1, 0, 0))) {
                        avatar.yRot = view * 65;avatar.xRot = view * 75;
                        var player = new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, narrow), 64, 64).bakeRoot(), narrow);
                        player.head.xRot = (float) Math.toRadians(avatar.xRot);player.head.yRot = (float) Math.toRadians(avatar.yRot);
                        if (crouch) { player.body.xRot = .5f;player.body.y = 3;player.leftLeg.y = player.rightLeg.y = 15; }
                        var root = new PoseStack();root.mulPose(referenceBase.last().pose());player.root().translateAndRotate(root);
                        var reference = emit(new FrozenModel.Builder().add(root, player.body).add(root, player.leftLeg).add(root, player.rightLeg).build().root(), new PoseStack());
                        WeaponPlayerPose.external(player, main, weapon.presentation(), motion);
                        var equippedBase = FirstPersonBodyRenderer.bodyBase(transforms, avatar, camera, offset);
                        check(equippedBase.last().pose().equals(referenceBase.last().pose()), "body anchor does not borrow equipped weapon head/rig yaw");
                        player.root().translateAndRotate(equippedBase);
                        var equipped = new FrozenModel.Builder().add(equippedBase, player.body).add(equippedBase, player.leftLeg).add(equippedBase, player.rightLeg).build();
                        equivalent(reference, emit(equipped.root(), new PoseStack()));
                    }
                }
            }
        }
    }
    private static void transparentRigBackground() {
        var clear = FirstPersonBodyRenderer.RIG_CLEAR_COLOR;
        check(clear.w() == 0, "rig clear must be transparent: opaque black would hide the world");
        var target = net.minecraft.client.renderer.RenderPipelines.ENTITY_OUTLINE_BLIT.getColorTargetStates().getFirst();
        var blend = target.blendFunction().orElseThrow().color();
        check(blend.sourceFactor() == com.mojang.renderpearl.api.pipeline.BlendFactor.SRC_ALPHA
            && blend.destFactor() == com.mojang.renderpearl.api.pipeline.BlendFactor.ONE_MINUS_SRC_ALPHA
            && blend.op() == com.mojang.renderpearl.api.pipeline.BlendOp.ADD, "native rig blit uses source alpha over destination");
        check(!target.writeAlpha(), "native rig blit preserves the world's destination alpha");
        var scene = new Vector3f(.2f, .4f, .7f);
        var emptyPixel = new Vector3f(clear.x(), clear.y(), clear.z()).mul(clear.w()).fma(1 - clear.w(), scene);
        check(emptyPixel.equals(scene), "cleared rig pixels preserve scene RGB");
        var armColor = new Vector3f(.65f, .48f, .35f);
        var armPixel = new Vector3f(armColor).mul(1).fma(0, scene);
        check(armPixel.equals(armColor), "opaque arm pixels still replace scene RGB");
    }
    private static List<Vector3f> emit(ModelPart model, PoseStack parent) {
        var vertices = new ArrayList<Vector3f>();
        var consumer = (VertexConsumer) Proxy.newProxyInstance(FirstPersonBodyTest.class.getClassLoader(), new Class<?>[] {VertexConsumer.class},
            (proxy, method, arguments) -> {
                if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, arguments);
                if (method.getName().equals("addVertex")) vertices.add(new Vector3f((float) arguments[0], (float) arguments[1], (float) arguments[2]));
                return proxy;
            });
        model.render(parent, consumer, 0xf000f0, 0, -1);
        return vertices;
    }
    private static void equivalent(List<Vector3f> expected, List<Vector3f> actual) {
        check(expected.size() == actual.size(), "same cuboid vertex count");
        // Child ordering is deliberately not part of the snapshot contract.
        for (var point : expected) check(actual.stream().anyMatch(other -> other.distance(point) < .00001f), "captured vertex equals independently rendered limb");
    }
    private static void check(boolean condition, String label) { checks++;if (!condition) throw new AssertionError(label); }
}
