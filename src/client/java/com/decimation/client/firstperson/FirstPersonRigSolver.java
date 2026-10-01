package com.decimation.client.firstperson;

/** Computes the shoulder pose from the same native pitch used by the player model. */
public final class FirstPersonRigSolver {
    private static final float UPWARD_SHOULDER_FORWARD_PIXELS = 6.0f;
    private static final float SAFETY_START = 65.0f;
    private static final float SAFETY_PITCH_DEGREES = 2.0f;

    private FirstPersonRigSolver() { }

    /** Minecraft player-model pitch is negative above the horizon and positive below it. */
    public static FirstPersonRigPose solve(float armLookPitchDegrees) {
        float pitch = Float.isFinite(armLookPitchDegrees)
            ? Math.max(-90.0f, Math.min(90.0f, armLookPitchDegrees)) : 0.0f;
        float upDegrees = Math.max(0.0f, -pitch);
        float up = smoothstep(upDegrees / 90.0f);
        float danger = smoothstep((upDegrees - SAFETY_START) / (90.0f - SAFETY_START));
        // Torso follow stays deferred. Downward anchoring uses the measured render-time eye.
        return new FirstPersonRigPose(radians(pitch), 0.0f, radians(pitch),
            UPWARD_SHOULDER_FORWARD_PIXELS * up, 0.0f,
            radians(SAFETY_PITCH_DEGREES * danger), 0.0f);
    }

    /** Shoulder position in the model's Y/Z plane, expressed in pixels. */
    public record ShoulderAnchor(float y, float z) { }

    /**
     * Keeps the rest shoulder at the same eye-relative location in the downward view.
     * The shoulder and arm take the same rotation, so view-space depth does not drift.
     */
    public static ShoulderAnchor solveDownwardShoulder(float restY, float restZ,
                                                        float eyeY, float eyeZ,
                                                        float pitchRadians) {
        if (!Float.isFinite(pitchRadians) || pitchRadians <= 0.0f
            || !Float.isFinite(eyeY) || !Float.isFinite(eyeZ)) {
            return new ShoulderAnchor(restY, restZ);
        }
        float pitch = Math.min((float) Math.PI / 2.0f, pitchRadians);
        float sin = (float) Math.sin(pitch);
        float cos = (float) Math.cos(pitch);
        float y = restY - eyeY;
        float z = restZ - eyeZ;
        return new ShoulderAnchor(eyeY + y * cos - z * sin,
            eyeZ + y * sin + z * cos);
    }

    private static float smoothstep(float amount) {
        float value = Math.max(0.0f, Math.min(1.0f, amount));
        return value * value * (3.0f - 2.0f * value);
    }

    private static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }
}
