package com.decimation.client.firstperson;

import com.decimation.client.gun.ClientWeaponPresentation;
import com.decimation.client.gun.WeaponItemModel;
import com.decimation.client.gun.WeaponSpecialRenderer;
import com.decimation.client.gun.WeaponViewTransforms;
import com.decimation.client.mixin.AvatarRendererAccessor;
import com.decimation.client.mixin.EquipmentRendererAccessor;
import com.decimation.client.mixin.FeatureDispatcherAccessor;
import com.decimation.module.gun.WeaponItem;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4fc;

/** Extraction owns all pose changes. Draw callbacks consume only the captured frame. */
public final class FirstPersonBodyRenderer {
    // JOML's no-argument Vector4f has w=1: specify alpha=0 for uncovered rig pixels.
    static final Vector4fc RIG_CLEAR_COLOR = new Vector4f(0, 0, 0, 0);
    private static final FirstPersonBodyRenderer INSTANCE = new FirstPersonBodyRenderer();
    private EntityModelSet models;
    private PlayerModel wide, slim;
    private ArmorModelSet<HumanoidModel<AvatarRenderState>> wideArmor, slimArmor;
    private EquipmentLayerRenderer equipment;
    private FeatureRenderDispatcher features;
    private StagedVertexBuffer vertices;
    private TextureTarget rigTarget;

    private FirstPersonBodyRenderer() { }
    public static void register() {
        LevelExtractionEvents.END_EXTRACTION.register(INSTANCE::extract);
        LevelRenderEvents.END_MAIN.register(INSTANCE::render);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> INSTANCE.close());
    }

    private void models(Minecraft client) {
        if (models == client.getEntityModels()) return;
        models = client.getEntityModels();
        wide = new PlayerModel(models.bakeLayer(ModelLayers.PLAYER), false);
        slim = new PlayerModel(models.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        wideArmor = ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, models, HumanoidModel<AvatarRenderState>::new);
        slimArmor = ArmorModelSet.bake(ModelLayers.PLAYER_SLIM_ARMOR, models, HumanoidModel<AvatarRenderState>::new);
        var access = (EquipmentRendererAccessor) client.getEntityRenderDispatcher();
        equipment = new EquipmentLayerRenderer(access.decimation$equipment(), access.decimation$palettes());
    }

    private void extract(LevelExtractionContext context) {
        var client = Minecraft.getInstance();
        var state = context.levelState().playerRenderState;
        var frameAccess = (FirstPersonFrameAccess) state;
        frameAccess.decimation$setFrame(null);
        var player = client.player;
        var avatar = state.avatarRenderState;
        if (player == null || avatar == null || !context.levelState().cameraRenderState.isFirstPerson
            || context.camera().entity() != player || !player.isAlive() || player.isSpectator() || player.isSleeping()
            || state.firstPersonHandsAndItems.isScoping) return;
        models(client);
        boolean narrow = avatar.skin.model() == PlayerModelType.SLIM;
        var model = narrow ? slim : wide;
        var armor = narrow ? slimArmor : wideArmor;
        model.setupAnim(avatar);
        var renderer = client.getEntityRenderDispatcher().getRenderer(avatar);
        var camera = context.camera().position();
        float delta = context.camera().getCameraEntityPartialTicks(context.deltaTracker());
        float bodyYaw = avatar.bodyRot;
        WeaponItemModel weaponModel = null;
        var main = player.getMainHandItem();
        if (main.getItem() instanceof WeaponItem weapon) {
            var baked = client.getModelManager().getItemModel(Identifier.parse(weapon.definition().id()));
            if (baked instanceof WeaponItemModel binding) weaponModel = binding;
        }
        var bodyBase = bodyBase((AvatarRendererAccessor) renderer, avatar, camera, renderer.getRenderOffset(avatar));
        var rootBase = copy(bodyBase);
        model.root().translateAndRotate(rootBase);
        var body = new FrozenModel.Builder().add(rootBase, model.body).add(rootBase, model.leftLeg).add(rootBase, model.rightLeg);
        var rig = new FrozenModel.Builder();
        var attachments = new ArrayList<HeldItem>();
        WeaponSpecialRenderer.Frame weaponFrame = null;
        boolean foil = false;
        PoseStack firingBase = rootBase, supportBase = rootBase;
        if (weaponModel != null) {
            var data = weaponModel.data();
            var sample = ClientWeaponPresentation.sample(player, Identifier.parse(data.definition().id()), delta, true);
            var motion = sample.motion();
            float hip = (1 - motion.aim()) * (1 - motion.carry());
            float tracking = WeaponRigPolicy.tracking(motion.relaxed());
            firingBase = base(renderer, avatar, camera, WeaponRigPolicy.yaw(bodyYaw, avatar.yRot, motion.relaxed()));
            model.root().translateAndRotate(firingBase);
            var firing = model.getArm(avatar.mainArm);
            var support = model.getArm(avatar.mainArm.getOpposite());
            // The mount always uses an unscaled firing limb, including reused model instances.
            firing.xScale = firing.yScale = firing.zScale = 1;
            float headYaw = (float) Math.toRadians(avatar.yRot * .1f * tracking);
            var rest = WeaponPlayerPose.interpolate(data.definition().presentation(), motion);
            WeaponPlayerPose.apply(firing, rest.mainHand(), avatar.mainArm == HumanoidArm.LEFT, 0, headYaw, 8, 12 * hip);
            var solved = FirstPersonRigSolver.solve(avatar.xRot * tracking);
            firing.x = Math.signum(firing.x) * (2.5f - 1.5f * hip) - 1;
            firing.y += -2 * hip + .5f;
            firing.z -= 2 * hip;
            firing.x -= (float) Math.sin(headYaw) * solved.shoulderForward();
            firing.z -= (float) Math.cos(headYaw) * solved.shoulderForward();
            support.x -= (float) Math.sin(headYaw) * solved.shoulderForward();
            support.z -= (float) Math.cos(headYaw) * solved.shoulderForward();
            if (solved.shoulderPitch() > 0) {
                var eye = new Matrix4f(firingBase.last().pose()).invert().transformPosition(new Vector3f());
                var anchor = FirstPersonRigSolver.solveDownwardShoulder(firing.y, firing.z, eye.y * 16, eye.z * 16, solved.shoulderPitch());
                firing.y = anchor.y();firing.z = anchor.z();
            }
            firingBase.translate(firing.x / 16, firing.y / 16, firing.z / 16);
            firingBase.rotate(Axis.XP, solved.shoulderPitch() + solved.cameraSafetyPitch());
            firingBase.translate(-firing.x / 16, -firing.y / 16, -firing.z / 16);
            float aim = motion.aim() * (1 - motion.sprint());
            if (aim > 0 && data.sightLine() != null) {
                // Solve from the neutral hand: recoil and reload must remain visible after alignment.
                var neutral = hand(model, avatar, avatar.mainArm, firingBase);
                neutral.mulPose(WeaponViewTransforms.rig(data.definition().presentation(), motion, avatar.mainArm == HumanoidArm.LEFT));
                var adjustment = WeaponAdsAlignment.solve(neutral.last().pose(), data.sightLine(), context.camera().forwardVector(), context.camera().upVector(), aim);
                var aligned = new PoseStack();aligned.mulPose(adjustment);aligned.mulPose(firingBase.last().pose());firingBase = aligned;
            }
            var breathing = new PoseStack();
            breathing.mulPose(WeaponRigPolicy.ambient(motion.ambient(), rootBase.last().pose(), context.camera().forwardVector(), context.camera().upVector(), motion.relaxed()));
            breathing.mulPose(firingBase.last().pose());firingBase = breathing;
            firing.xRot -= (float) Math.toRadians(motion.recoilPitch());
            firing.yRot += (float) Math.toRadians(motion.recoilYaw());
            boolean reloadInHand = ReloadFiringArm.apply(firing, data, sample, avatar.mainArm == HumanoidArm.LEFT, true);
            // Capture the hand before shrinking the skin, so weapon dimensions stay unchanged.
            var hand = hand(model, avatar, avatar.mainArm, firingBase);
            hand.mulPose(WeaponViewTransforms.rig(data.definition().presentation(), motion, avatar.mainArm == HumanoidArm.LEFT));
            weaponFrame = new WeaponSpecialRenderer.Frame(new Matrix4f(hand.last().pose()), sample, reloadInHand);
            foil = main.hasFoil();
            if (player.getOffhandItem().isEmpty() && data.supportGrip() != null) {
                WeaponSpecialRenderer.applyRoot(hand, data, sample, reloadInHand);
                var transform = WeaponViewTransforms.firstPerson(data.definition().presentation(), motion);
                float size = transform.scale() * 1.32f * (1 + .30f * hip);
                var grip = data.supportGrip();
                // OBJ +X follows the barrel; +Z is sideways. Keep rightward movement for either firing hand.
                var idle = new Vector3f(grip.x() + 1 / (16 * size * (1 - .25f * hip)),
                    grip.y() - 2f / (16 * size), grip.z() + (avatar.mainArm == HumanoidArm.LEFT ? -.4f : .4f) / (16 * size));
                var localTarget = ReloadSupportArm.target(data, sample, idle);
                ReloadSupportArm.offset(localTarget, data, sample, hand.last().pose(), avatar.mainArm == HumanoidArm.LEFT);
                var target = hand.last().pose().transformPosition(localTarget);
                new Matrix4f(rootBase.last().pose()).invert().transformPosition(target);
                var supportPose = FirstPersonSupportArmSolver.pointAt(target.x * 16 - support.x, target.y * 16 - support.y, target.z * 16 - support.z);
                if (supportPose != null) {
                    support.xRot = supportPose.pitch();support.yRot = supportPose.yaw();support.zRot = 0;support.yScale = supportPose.lengthScale();
                }
                ReloadSupportArm.apply(support, data, sample, avatar.mainArm == HumanoidArm.LEFT);
            } else {
                // An occupied offhand keeps vanilla's extracted holding/use pose.
                attachments.add(item(client, player.getOffhandItem(), model, avatar, avatar.mainArm.getOpposite(), supportBase));
            }
            firing.xScale = firing.zScale = WeaponRigPolicy.skinThickness(aim);firing.yScale = 1;
            support.xScale = support.zScale = .68f;
            WeaponPlayerPose.sleeves(model);
            rig.add(firingBase, firing).add(supportBase, support);
        } else {
            body.add(rootBase, model.rightArm).add(rootBase, model.leftArm);
            attachments.add(item(client, player.getMainHandItem(), model, avatar, avatar.mainArm, rootBase));
            attachments.add(item(client, player.getOffhandItem(), model, avatar, avatar.mainArm.getOpposite(), rootBase));
        }
        var armorBody = new ArrayList<Armor>();
        var armorRig = new ArrayList<Armor>();
        for (var slot : new EquipmentSlot[] {EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            var stack = switch (slot) { case CHEST -> avatar.chestEquipment;case LEGS -> avatar.legsEquipment;default -> avatar.feetEquipment; };
            if (stack == null || !HumanoidArmorLayer.shouldRender(stack, slot)) continue;
            var worn = armor.get(slot);
            copyLimbs(model, worn);
            var bodyArmor = new FrozenModel.Builder();
            var rigArmor = new FrozenModel.Builder();
            boolean hasTorso = slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS;
            if (hasTorso) bodyArmor.add(rootBase, worn.body);
            if (slot != EquipmentSlot.CHEST) bodyArmor.add(rootBase, worn.leftLeg).add(rootBase, worn.rightLeg);
            if (slot == EquipmentSlot.CHEST) {
                if (weaponModel != null) {
                    rigArmor.add(firingBase, worn.getArm(avatar.mainArm)).add(supportBase, worn.getArm(avatar.mainArm.getOpposite()));
                } else bodyArmor.add(rootBase, worn.rightArm).add(rootBase, worn.leftArm);
            }
            armorBody.add(new Armor(bodyArmor.build(), stack.copy(), slot));
            if (weaponModel != null && slot == EquipmentSlot.CHEST) armorRig.add(new Armor(rigArmor.build(), stack.copy(), slot));
        }
        frameAccess.decimation$setFrame(new Frame(body.build(), rig.build(), renderer.getTextureLocation(avatar), !avatar.isInvisible,
            avatar.lightCoords, LivingEntityRenderer.getOverlayCoords(avatar, 0), List.copyOf(armorBody), List.copyOf(armorRig),
            List.copyOf(attachments), weaponModel == null ? null : weaponModel.renderer(), weaponFrame, foil, equipment));
    }

    static PoseStack bodyBase(AvatarRendererAccessor renderer, AvatarRenderState avatar,
                              net.minecraft.world.phys.Vec3 camera, net.minecraft.world.phys.Vec3 offset) {
        // The body anchor follows body yaw for every held item; rig yaw belongs only to the firing arm.
        return base(renderer, avatar, camera, offset, avatar.bodyRot);
    }
    private static PoseStack base(AvatarRenderer<?> renderer, AvatarRenderState avatar, net.minecraft.world.phys.Vec3 camera, float yaw) {
        return base((AvatarRendererAccessor) renderer, avatar, camera, renderer.getRenderOffset(avatar), yaw);
    }
    private static PoseStack base(AvatarRendererAccessor renderer, AvatarRenderState avatar, net.minecraft.world.phys.Vec3 camera,
                                  net.minecraft.world.phys.Vec3 offset, float yaw) {
        var poses = new PoseStack();
        double radians = Math.toRadians(yaw);
        poses.translate(avatar.x - camera.x + offset.x + Math.sin(radians) * 3 / 16,
            avatar.y - camera.y + offset.y, avatar.z - camera.z + offset.z - Math.cos(radians) * 3 / 16);
        poses.scale(avatar.scale, avatar.scale, avatar.scale);
        renderer.decimation$rotations(avatar, poses, yaw, avatar.scale);
        poses.scale(-1, -1, 1);
        renderer.decimation$scale(avatar, poses);
        poses.translate(0, -1.501f, 0);
        return poses;
    }
    private static PoseStack copy(PoseStack poses) { var result = new PoseStack();result.mulPose(poses.last().pose());return result; }
    private static PoseStack hand(PlayerModel model, AvatarRenderState state, HumanoidArm arm, PoseStack parent) {
        var poses = copy(parent);
        // translateToHand includes root; parent already has it, so use the limb directly.
        var limb = model.getArm(arm);
        float slimShift = state.skin.model() == PlayerModelType.SLIM ? (arm == HumanoidArm.RIGHT ? 1 : -1) * .5f : 0;
        poses.translate(slimShift / 16, 0, 0);
        limb.translateAndRotate(poses);
        poses.rotateDegrees(Axis.XP, -90);poses.rotateDegrees(Axis.YP, 180);
        poses.translate((arm == HumanoidArm.LEFT ? -1f : 1f) / 16, 2f / 16, -10f / 16);
        return poses;
    }
    private static HeldItem item(Minecraft client, ItemStack stack, PlayerModel model, AvatarRenderState state, HumanoidArm arm, PoseStack parent) {
        var extracted = new ItemStackRenderState();
        client.getItemModelResolver().updateForLiving(extracted, stack, arm == HumanoidArm.LEFT ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, client.player);
        return new HeldItem(extracted, new Matrix4f(hand(model, state, arm, parent).last().pose()));
    }
    private static void copyLimbs(PlayerModel from, HumanoidModel<?> to) {
        to.body.loadPose(from.body.storePose());to.leftLeg.loadPose(from.leftLeg.storePose());to.rightLeg.loadPose(from.rightLeg.storePose());
        copyArm(from.leftArm, to.leftArm);copyArm(from.rightArm, to.rightArm);
    }

    private static void copyArm(ModelPart from, ModelPart to) {
        to.loadPose(from.storePose());to.xScale = from.xScale;to.yScale = from.yScale;to.zScale = from.zScale;
    }

    private void render(LevelRenderContext context) {
        var frame = ((FirstPersonFrameAccess) context.levelState().playerRenderState).decimation$getFrame();
        if (frame == null) return;
        var game = context.gameRenderer();
        var main = game.mainRenderTarget();
        if (features == null) {
            var client = Minecraft.getInstance();
            features = new FeatureRenderDispatcher(game.renderBuffers(), client.getModelManager(), client.getAtlasManager(), client.font, game.gameRenderState());
            vertices = new StagedVertexBuffer(() -> "Decimation captured first-person geometry", 4096);
            ((FeatureDispatcherAccessor) features).decimation$buffer(vertices);
        }
        boolean weapon = frame.weapon() != null;
        if (weapon) {
            if (rigTarget == null || rigTarget.width != main.width || rigTarget.height != main.height
                || rigTarget.getColorTexture().getFormat() != main.getColorTexture().getFormat()
                || rigTarget.getDepthTexture().getFormat() != main.getDepthTexture().getFormat()) {
                if (rigTarget != null) rigTarget.destroyBuffers();
                rigTarget = new TextureTarget("Decimation weapon rig", main.width, main.height, main.getColorTexture().getFormat(), main.getDepthTexture().getFormat());
            }
            rigTarget.copyDepthFrom(main); // Before the body writes any depth.
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(rigTarget.getColorTexture(), RIG_CLEAR_COLOR);
        }
        var body = new SubmitNodeStorage();
        if (frame.skinVisible()) skin(frame.body(), frame, body);
        armor(frame.bodyArmor(), frame, body);
        if (!weapon) items(frame, body);
        draw(body, main);
        if (weapon) {
            var rig = new SubmitNodeStorage();
            if (frame.skinVisible()) skin(frame.rigSkin(), frame, rig);
            armor(frame.rigArmor(), frame, rig);
            frame.weapon().submit(frame.weaponFrame(), new PoseStack(), rig, frame.light(), OverlayTexture.NO_OVERLAY, frame.foil(), 0);
            items(frame, rig);
            draw(rig, rigTarget);
            rigTarget.blitAndBlendToTexture(main.getColorTextureView(), main.getDepthTextureView());
        }
    }
    private static void skin(FrozenModel model, Frame frame, SubmitNodeCollector collector) {
        collector.submitModel(model, null, new PoseStack(), RenderTypes.entityCutout(frame.texture()), frame.light(), frame.overlay(), -1, null, 0);
    }
    private static void armor(List<Armor> armor, Frame frame, SubmitNodeCollector collector) {
        for (var layer : armor) {
            var equipped = layer.stack().get(DataComponents.EQUIPPABLE);
            if (equipped != null && equipped.assetId().isPresent()) frame.equipment().renderLayers(
                layer.slot() == EquipmentSlot.LEGS ? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS : EquipmentClientInfo.LayerType.HUMANOID,
                equipped.assetId().get(), layer.model(), null, layer.stack(), new PoseStack(), collector, frame.light(), 0);
        }
    }
    private static void items(Frame frame, SubmitNodeCollector collector) {
        for (var held : frame.items()) {
            var poses = new PoseStack();poses.mulPose(held.pose());
            held.state().submit(poses, collector, frame.light(), OverlayTexture.NO_OVERLAY, 0);
        }
    }
    private void draw(SubmitNodeStorage nodes, RenderTarget target) {
        try (var prepared = features.prepareFrame(nodes)) {
            var descriptor = RenderPassDescriptor.builder(() -> "Decimation first-person layer")
                .withColorAttachment(target.getColorTextureView()).withDepthAttachment(target.getDepthTextureView()).build();
            try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor)) {
                RenderSystem.bindDefaultUniforms(pass);
                FeatureRenderDispatcher.renderAllFeatures(pass, prepared);
            }
        } finally { vertices.endFrame(); }
    }
    private void close() {
        if (rigTarget != null) { rigTarget.destroyBuffers();rigTarget = null; }
        if (features != null) { features.close();features = null; }
        if (vertices != null) { vertices.close();vertices = null; }
    }

    public record Armor(FrozenModel model, ItemStack stack, EquipmentSlot slot) { }
    public record HeldItem(ItemStackRenderState state, Matrix4fc pose) { }
    public record Frame(FrozenModel body, FrozenModel rigSkin, Identifier texture, boolean skinVisible, int light, int overlay,
                        List<Armor> bodyArmor, List<Armor> rigArmor, List<HeldItem> items,
                        WeaponSpecialRenderer weapon, WeaponSpecialRenderer.Frame weaponFrame, boolean foil, EquipmentLayerRenderer equipment) { }
}
