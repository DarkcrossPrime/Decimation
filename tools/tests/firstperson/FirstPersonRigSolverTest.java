package com.decimation.client.firstperson;

/** Dependency-free behavior checks. Run with tools/tests/firstperson/run.sh. */
public final class FirstPersonRigSolverTest {
    public static void main(String[] args) {
        FirstPersonRigPose neutral = solve(0.0f);
        near(neutral.shoulderPitch(), 0.0f, "neutral shoulder");
        near(neutral.shoulderForward(), 0.0f, "neutral shoulder translation");
        near(neutral.cameraSafetyPitch(), 0.0f, "neutral safety rotation");
        for (int degrees = -90; degrees <= 90; degrees++) {
            FirstPersonRigPose pose = solve(degrees);
            near(pose.shoulderPitch(), (float) Math.toRadians(degrees), "shoulder carries viewing pitch once");
            if (Math.abs(degrees) <= 65) {
                near(pose.cameraSafetyPitch(), 0.0f, "normal-view safety rotation");
            }
            if (degrees >= 0) near(pose.shoulderForward(), 0.0f, "downward shoulder unchanged");
            require(pose.shoulderForward() >= 0.0f && pose.shoulderForward() <= 6.0f,
                "upward shoulder travel stays within six pixels");
            require(Math.abs(pose.cameraSafetyPitch()) <= Math.toRadians(2.01),
                "safety angle stays bounded");
            FirstPersonRigPose mirrored = solve(-degrees);
            near(mirrored.shoulderPitch(), -pose.shoulderPitch(), "symmetric shoulder aim");
            if (degrees >= 0) {
                near(pose.cameraSafetyPitch(), 0.0f, "no downward clearance rotation");
            }
            if (degrees != 0) require(pose.cameraSafetyPitch() * pose.shoulderPitch() <= 0.0f,
                "safety eases away from the extreme");
        }
        near(solve(-90).shoulderForward(), 6.0f, "full upward shoulder travel");
        require(solve(-0.01f).shoulderForward() < 0.00001f,
            "smooth upward shoulder entry");
        float previous = 0.0f;
        for (int up = 0; up <= 90; up++) {
            float travel = solve(-up).shoulderForward();
            require(travel >= previous, "upward shoulder advances monotonically");
            previous = travel;
        }
        require(solve(-65.01f).cameraSafetyPitch() < 0.00001f,
            "smooth safety entry");
        require(solve(1000).equals(solve(90)), "positive clamp");
        require(solve(-1000).equals(solve(-90)), "negative clamp");
        for (float invalid : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
            require(solve(invalid).equals(neutral), "invalid input has a neutral fallback");
        near(solve(-30).shoulderForward(), 14.0f / 9.0f, "upward shoulder travel at 30 degrees");
        near(solve(-60).shoulderForward(), 40.0f / 9.0f, "upward shoulder travel at 60 degrees");
        // Undo the camera rotation: the shoulder must recover its rest screen
        // height and depth throughout a downward sweep, for different eye poses.
        for (float eyeY : new float[] {-3.6f, -2.0f, 1.2f}) {
            for (float eyeZ : new float[] {-3.2f, -3.0f, -1.0f}) {
                for (int degrees = 0; degrees <= 90; degrees++) {
                    float pitch = (float) Math.toRadians(degrees);
                    var anchor = FirstPersonRigSolver.solveDownwardShoulder(
                        0.0f, -2.0f, eyeY, eyeZ, pitch);
                    float y = anchor.y() - eyeY;
                    float z = anchor.z() - eyeZ;
                    float cos = (float) Math.cos(pitch);
                    float sin = (float) Math.sin(pitch);
                    near(y * cos + z * sin, -eyeY, "downward shoulder screen height preserved");
                    near(-y * sin + z * cos, -2.0f - eyeZ, "downward shoulder view depth preserved");
                }
                for (int degrees = -90; degrees <= 0; degrees++) {
                    var anchor = FirstPersonRigSolver.solveDownwardShoulder(
                        0.0f, -2.0f, eyeY, eyeZ, (float) Math.toRadians(degrees));
                    near(anchor.y(), 0.0f, "upward anchor path untouched");
                    near(anchor.z(), -2.0f, "upward anchor depth untouched");
                }
            }
        }
        System.out.println("First-person rig solver checks passed.");
    }

    private static FirstPersonRigPose solve(float degrees) {
        return FirstPersonRigSolver.solve(degrees);
    }

    private static void near(float actual, float expected, String message) {
        require(Math.abs(actual - expected) < 0.00001f, message + ": " + actual + " != " + expected);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
