package com.decimation.client.firstperson;

import com.decimation.Decimation;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Scoped state for one local-player render; model changes never escape this pass. */
public final class FirstPersonRenderState {
    public static final float LOOK_ROTATION_FACTOR = 0.1f;
    // The accepted neutral weapon transform. Keep tuning independent of the rig solver.
    public static final float HIP_WEAPON_RIGHT = -0.0175f;
    public static final float HIP_WEAPON_LOWER = 0.5f / 16.0f;
    public static final float HIP_WEAPON_BACKSET = -1.0f / 16.0f;
    public static final float HIP_WEAPON_PITCH = 3.0f;
    public static final float HIP_WEAPON_SCALE = 1.30f;
    public static final float HIP_WEAPON_LENGTH_SCALE = 0.75f;
    private static final float ARM_WIDTH_SCALE = 0.68f;
    private static final float FIRING_ARM_CAMERA_SIDE = 2.5f;
    private static final float FIRING_ARM_HIP_INSET = 1.5f;
    private static final float FIRING_ARM_HIP_LIFT = 2.0f;
    private static final float FIRING_ARM_HIP_FORWARD = 2.0f;
    // Model -X is screen-right and model +Y is down in the player renderer.
    // Shift the shoulder so skin, sleeves, armor and held gun move together.
    private static final float FIRING_ARM_RIGHT_OFFSET = 1.0f;
    private static final float FIRING_ARM_DOWN_OFFSET = 0.5f;
    private static final float PIXEL = 1.0f / 16.0f;

    private enum Part { TORSO, FIRING_ARM, SUPPORT_ARM }

    private static ClientPlayerEntity player;
    private static FirstPersonRenderPass renderPass = FirstPersonRenderPass.FULL;
    private static int heldItemDepth;
    private static Vector3f supportGrip;
    private static FirstPersonRigPose rigPose;
    private static float viewDirectionY;
    private static float shoulderX;
    private static float shoulderY;
    private static float shoulderZ;
    private static boolean downwardShoulderSolved;
    private static boolean rigParentApplied;
    private static int lastDiagnosticBand = Integer.MIN_VALUE;
    private static final Map<ModelPart, Part> rigParts = new IdentityHashMap<>();
    private static final Map<ModelPart, float[]> originals = new IdentityHashMap<>();

    private FirstPersonRenderState() { }

    static void begin(ClientPlayerEntity localPlayer, float cameraForwardY) {
        if (player != null) throw new IllegalStateException("Nested first-person body render");
        player = localPlayer;
        viewDirectionY = cameraForwardY;
        rigPose = null;
        rigParentApplied = false;
        downwardShoulderSolved = false;
        supportGrip = null;
    }

    static void end() {
        logRigDiagnostic();
        originals.forEach((part, original) -> {
            part.xScale = original[0];
            part.yScale = original[1];
            part.zScale = original[2];
            part.pivotX = original[3];
            part.pivotY = original[4];
            part.pivotZ = original[5];
            part.pitch = original[6];
            part.yaw = original[7];
            part.roll = original[8];
        });
        originals.clear();
        rigParts.clear();
        rigPose = null;
        renderPass = FirstPersonRenderPass.FULL;
        heldItemDepth = 0;
        supportGrip = null;
        viewDirectionY = 0.0f;
        player = null;
    }

    public static void setRenderPass(FirstPersonRenderPass pass) {
        renderPass = pass;
    }

    public static boolean isBodyPass() {
        return player != null && renderPass == FirstPersonRenderPass.BODY;
    }

    public static boolean isRigPass() {
        return player != null && (renderPass == FirstPersonRenderPass.RIG
            || renderPass == FirstPersonRenderPass.SUPPORT);
    }

    public static boolean isSupportPass() {
        return player != null && renderPass == FirstPersonRenderPass.SUPPORT;
    }

    /** Capture this frame's main-hand gun position, after all held-item transforms. */
    public static void captureSupportGrip(ItemStack stack, MatrixStack matrices,
                                           FirstPersonSupportArmSolver.Grip grip) {
        if (player == null || renderPass != FirstPersonRenderPass.RIG || grip == null
            || stack.getItem() != player.getMainHandStack().getItem()
            || !player.getOffHandStack().isEmpty()) return;
        supportGrip = matrices.peek().getPositionMatrix().transformPosition(
            grip.x(), grip.y(), grip.z(), new Vector3f());
    }

    public static void beginHeldItems() { heldItemDepth++; }
    public static void endHeldItems() { heldItemDepth--; }

    /** Register skin parts before feature models copy their transforms. */
    public static void registerPlayerParts(PlayerEntityModel<?> model, boolean rightHanded) {
        rigParts.put(model.body, Part.TORSO);
        rigParts.put(model.jacket, Part.TORSO);
        rigParts.put(model.rightLeg, Part.TORSO);
        rigParts.put(model.leftLeg, Part.TORSO);
        rigParts.put(model.rightPants, Part.TORSO);
        rigParts.put(model.leftPants, Part.TORSO);
        rigParts.put(model.rightArm, rightHanded ? Part.FIRING_ARM : Part.SUPPORT_ARM);
        rigParts.put(model.leftArm, rightHanded ? Part.SUPPORT_ARM : Part.FIRING_ARM);
        copyRigParent(model.rightArm, model.rightSleeve);
        copyRigParent(model.leftArm, model.leftSleeve);
    }

    /** Unknown feature models stay with the body; held-item models belong to the rig. */
    public static boolean shouldRenderPart(ModelPart part) {
        if (player == null || renderPass == FirstPersonRenderPass.FULL) return true;
        Part parent = rigParts.get(part);
        if (renderPass == FirstPersonRenderPass.SUPPORT) return parent == Part.SUPPORT_ARM;
        if (parent == null) return heldItemDepth > 0 || renderPass == FirstPersonRenderPass.BODY;
        boolean arm = parent == Part.FIRING_ARM || parent == Part.SUPPORT_ARM;
        return renderPass == FirstPersonRenderPass.RIG ? parent == Part.FIRING_ARM : !arm;
    }

    /** Called before the model head pitch is scaled or the arm rest pose is applied. */
    public static void solveRig(float armLookPitchDegrees) {
        if (player != null) rigPose = FirstPersonRigSolver.solve(armLookPitchDegrees);
    }

    public static boolean isRenderingBody() { return player != null; }

    public static boolean isRenderingPlayer(Object entity) { return player != null && entity == player; }

    /** Installs rest anchors; the downward render-time solve uses the measured eye. */
    public static void prepareWeaponRig(PlayerEntityModel<?> model, ModelPart firingArm,
                                         ModelPart supportArm, float hipWeight) {
        rememberArm(firingArm);
        rememberArm(supportArm);
        rememberArm(model.rightSleeve);
        rememberArm(model.leftSleeve);
        downwardShoulderSolved = false;
        float[] original = originals.get(firingArm);
        firingArm.pivotX = Math.signum(original[3])
            * (FIRING_ARM_CAMERA_SIDE - FIRING_ARM_HIP_INSET * hipWeight)
            - FIRING_ARM_RIGHT_OFFSET;
        firingArm.pivotY = original[4] - FIRING_ARM_HIP_LIFT * hipWeight
            + FIRING_ARM_DOWN_OFFSET;
        firingArm.pivotZ = original[5] - FIRING_ARM_HIP_FORWARD * hipWeight;
        // Move the actual model anchor in horizontal body-local forward (-Z).
        // This clears the arm shaft from the eye when looking vertically upward;
        // moving along the arm/aim axis alone leaves that shaft in the eye column.
        float forward = rigPose == null ? 0.0f : rigPose.shoulderForward();
        firingArm.pivotX -= (float) Math.sin(model.head.yaw) * forward;
        firingArm.pivotZ -= (float) Math.cos(model.head.yaw) * forward;
        // Give the support shoulder the same upward clearance. Use the saved
        // rest anchor so the body, weapon and support passes cannot accumulate it.
        float[] supportOriginal = originals.get(supportArm);
        supportArm.pivotX = supportOriginal[3] - (float) Math.sin(model.head.yaw) * forward;
        supportArm.pivotZ = supportOriginal[5] - (float) Math.cos(model.head.yaw) * forward;
        shoulderX = firingArm.pivotX;
        shoulderY = firingArm.pivotY;
        shoulderZ = firingArm.pivotZ;
        rigParts.put(model.body, Part.TORSO);
        rigParts.put(model.jacket, Part.TORSO);
        rigParts.put(firingArm, Part.FIRING_ARM);
        rigParts.put(supportArm, Part.SUPPORT_ARM);
        copyRigParent(model.rightArm, model.rightSleeve);
        copyRigParent(model.leftArm, model.leftSleeve);
    }

    /** Runs before vanilla ModelPart.rotate, for skin, sleeves, armor AND held items. */
    public static void applyRigParent(ModelPart part, MatrixStack matrices) {
        Part role = rigParts.get(part);
        if (role == Part.SUPPORT_ARM && isSupportPass() && supportGrip != null) {
            // The support pass uses native torso yaw. Convert the actual gun's
            // view-space grip into this shoulder's model space, with no frame lag.
            Vector3f target = new Matrix4f(matrices.peek().getPositionMatrix()).invert()
                .transformPosition(supportGrip.x(), supportGrip.y(), supportGrip.z(), new Vector3f());
            FirstPersonSupportArmSolver.Pose pose = FirstPersonSupportArmSolver.pointAt(
                target.x() / PIXEL - part.pivotX,
                target.y() / PIXEL - part.pivotY,
                target.z() / PIXEL - part.pivotZ);
            if (pose != null) {
                rememberArm(part);
                part.pitch = pose.pitch();
                part.yaw = pose.yaw();
                part.roll = 0.0f;
                part.yScale = pose.lengthScale();
            }
            return;
        }
        if (rigPose == null || role != Part.FIRING_ARM) return;
        rigParentApplied = true;
        if (rigPose.shoulderPitch() > 0.0f) {
            if (!downwardShoulderSolved) {
                // This matrix already contains the body offset, pose and renderer
                // scale. Its inverse gives the actual eye in model space without
                // assuming a standing eye height or a fixed crouch offset.
                Vector3f eye = new Matrix4f(matrices.peek().getPositionMatrix()).invert()
                    .transformPosition(0.0f, 0.0f, 0.0f, new Vector3f());
                FirstPersonRigSolver.ShoulderAnchor anchor =
                    FirstPersonRigSolver.solveDownwardShoulder(shoulderY, shoulderZ,
                        eye.y() / PIXEL, eye.z() / PIXEL, rigPose.shoulderPitch());
                shoulderY = anchor.y();
                shoulderZ = anchor.z();
                downwardShoulderSolved = true;
                // Sleeves were copied before the first arm draw. Move them with
                // the skin now; armor copied later inherits this same anchor.
                rigParts.forEach((rigPart, parent) -> {
                    if (parent == Part.FIRING_ARM) {
                        rigPart.pivotY = shoulderY;
                        rigPart.pivotZ = shoulderZ;
                    }
                });
            }
            part.pivotY = shoulderY;
            part.pivotZ = shoulderZ;
        }
        rotateAround(matrices, shoulderX, shoulderY, shoulderZ,
            rigPose.shoulderPitch() + rigPose.cameraSafetyPitch());
        // The support pass solves its own shoulder-to-grip orientation.
    }

    /** Temporary, band-limited diagnostics for the in-game clipping investigation. */
    private static void logRigDiagnostic() {
        if (rigPose == null) return;
        float pitch = (float) Math.toDegrees(rigPose.shoulderPitch());
        if (Math.abs(pitch) < 30.0f) {
            lastDiagnosticBand = Integer.MIN_VALUE;
            return;
        }
        int band = Math.round(pitch / 30.0f);
        if (band == lastDiagnosticBand) return;
        lastDiagnosticBand = band;
        Decimation.LOGGER.info("[FirstPersonRig] modelPitch={} cameraForwardY={} shoulderForwardPx={} "
                + "shoulderPivot=({}, {}, {}) parentApplied={}",
            pitch, viewDirectionY, rigPose.shoulderForward(), shoulderX, shoulderY,
            shoulderZ, rigParentApplied);
    }

    private static void rotateAround(MatrixStack matrices, float x, float y, float z, float pitch) {
        if (pitch == 0.0f) return;
        matrices.translate(x * PIXEL, y * PIXEL, z * PIXEL);
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(pitch));
        matrices.translate(-x * PIXEL, -y * PIXEL, -z * PIXEL);
    }

    /** Carries the same hierarchy onto feature models when vanilla copies their rest pose. */
    public static void copyRigParent(ModelPart source, ModelPart target) {
        if (!isRenderingBody()) return;
        Part role = rigParts.get(source);
        if (role != null) rigParts.put(target, role);
        else rigParts.remove(target);
    }

    public static void shrinkArms(PlayerEntityModel<?> model) {
        shrinkArm(model.rightArm);
        shrinkArm(model.leftArm);
        shrinkArm(model.rightSleeve);
        shrinkArm(model.leftSleeve);
    }

    private static void shrinkArm(ModelPart arm) {
        rememberArm(arm);
        float[] original = originals.get(arm);
        arm.xScale = original[0] * ARM_WIDTH_SCALE;
        arm.zScale = original[2] * ARM_WIDTH_SCALE;
    }

    public static void rememberArm(ModelPart arm) {
        if (player != null) originals.computeIfAbsent(arm,
            part -> new float[] {part.xScale, part.yScale, part.zScale,
                part.pivotX, part.pivotY, part.pivotZ, part.pitch, part.yaw, part.roll});
    }

    public static boolean isShrunkArm(ModelPart arm) { return originals.containsKey(arm); }

    public static void unscaleForHeldItem(ModelPart arm) {
        float[] original = originals.get(arm);
        if (original == null) return;
        arm.xScale = original[0];
        arm.yScale = original[1];
        arm.zScale = original[2];
    }
}
