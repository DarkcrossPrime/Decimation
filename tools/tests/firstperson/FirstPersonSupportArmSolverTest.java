package com.decimation.client.firstperson;

import com.decimation.client.content.ObjModel;
import java.util.List;

public final class FirstPersonSupportArmSolverTest {
    public static void main(String[] args) {
        ObjModel model = new ObjModel(List.of(
            new ObjModel.Vertex(-10, -6, -1), new ObjModel.Vertex(0, -6, -1),
            new ObjModel.Vertex(0, -6, 1), new ObjModel.Vertex(-10, -6, 1),
            new ObjModel.Vertex(2, 2, -.5f), new ObjModel.Vertex(20, 2, -.5f),
            new ObjModel.Vertex(20, 2, .5f), new ObjModel.Vertex(2, 2, .5f),
            new ObjModel.Vertex(2, 4, -.5f), new ObjModel.Vertex(20, 4, -.5f)), List.of(),
            List.of(face(0, 1, 2, 3), face(4, 5, 6, 7), face(4, 5, 9, 8)));
        var grip = FirstPersonSupportArmSolver.midBarrelGrip(model);
        near(grip.x(), 10, "midpoint uses the forward section, excluding the stock");
        near(grip.y(), 2, "grip rests beneath the barrel section");
        near(grip.z(), 0, "grip is centered across the barrel");
        require(FirstPersonSupportArmSolver.midBarrelGrip(
            new ObjModel(List.of(), List.of(), List.of())) == null, "empty-model fallback");
        for (float x : new float[] {-12, -3, 0, 7}) {
            for (float y : new float[] {-18, -2, 0, 9}) {
                for (float z : new float[] {-25, -3, 0, 9}) {
                    var pose = FirstPersonSupportArmSolver.pointAt(x, y, z);
                    if (x == 0 && y == 0 && z == 0) {
                        require(pose == null, "coincident shoulder and hand");
                        continue;
                    }
                    float length = 10 * pose.lengthScale();
                    float sinPitch = (float) Math.sin(pose.pitch());
                    near((float) Math.sin(pose.yaw()) * sinPitch * length, x, "hand X reaches grip");
                    near((float) Math.cos(pose.pitch()) * length, y, "hand Y reaches grip");
                    near((float) Math.cos(pose.yaw()) * sinPitch * length, z, "hand Z reaches grip");
                }
            }
        }
        require(FirstPersonSupportArmSolver.pointAt(Float.NaN, 1, 2) == null, "invalid target fallback");
        System.out.println("Support-arm midpoint and shoulder-to-hand checks passed.");
    }

    private static ObjModel.Face face(int a, int b, int c, int d) {
        return new ObjModel.Face("barrel", new int[] {a, b, c, d}, new int[] {-1, -1, -1, -1});
    }

    private static void near(float actual, float expected, String message) {
        require(Math.abs(actual - expected) < 0.00001f, message + ": " + actual + " != " + expected);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
