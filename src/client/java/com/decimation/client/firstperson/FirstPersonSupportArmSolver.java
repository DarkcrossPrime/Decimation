package com.decimation.client.firstperson;

import com.decimation.client.content.ObjModel;

/** First placement: a straight arm from the real shoulder to the gun's underside. */
public final class FirstPersonSupportArmSolver {
    // Vanilla arm cuboids end ten pixels below their shoulder pivot.
    private static final float HAND_REACH_PIXELS = 10.0f;

    public record Grip(float x, float y, float z) { }
    public record Pose(float pitch, float yaw, float lengthScale) { }

    private FirstPersonSupportArmSolver() { }

    /** Recovered gun models point along +X; the origin separates stock and forward section. */
    public static Grip midBarrelGrip(ObjModel model) {
        if (model.vertices().isEmpty()) return null;
        float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (ObjModel.Vertex vertex : model.vertices()) {
            minX = Math.min(minX, vertex.x());
            maxX = Math.max(maxX, vertex.x());
            minY = Math.min(minY, vertex.y());
            minZ = Math.min(minZ, vertex.z());
            maxZ = Math.max(maxZ, vertex.z());
        }
        float start = Math.max(0.0f, minX);
        if (start > maxX) start = minX;
        float middle = (start + maxX) * 0.5f;
        float underside = Float.POSITIVE_INFINITY;
        float left = Float.POSITIVE_INFINITY, right = Float.NEGATIVE_INFINITY;
        for (ObjModel.Face face : model.faces()) {
            float faceMinX = Float.POSITIVE_INFINITY, faceMaxX = Float.NEGATIVE_INFINITY;
            for (int index : face.vertexIndices()) {
                float x = model.vertices().get(index).x();
                faceMinX = Math.min(faceMinX, x);
                faceMaxX = Math.max(faceMaxX, x);
            }
            if (middle < faceMinX || middle > faceMaxX) continue;
            for (int index : face.vertexIndices()) {
                ObjModel.Vertex vertex = model.vertices().get(index);
                underside = Math.min(underside, vertex.y());
                left = Math.min(left, vertex.z());
                right = Math.max(right, vertex.z());
            }
        }
        if (!Float.isFinite(underside)) {
            underside = minY;
            left = minZ;
            right = maxZ;
        }
        return new Grip(middle, underside, (left + right) * 0.5f);
    }

    /** Points local +Y toward the grip using vanilla ModelPart's yaw/pitch order. */
    public static Pose pointAt(float dx, float dy, float dz) {
        if (!Float.isFinite(dx) || !Float.isFinite(dy) || !Float.isFinite(dz)) return null;
        float horizontal = (float) Math.hypot(dx, dz);
        float distance = (float) Math.hypot(horizontal, dy);
        if (distance < 0.0001f) return null;
        return new Pose(-(float) Math.atan2(horizontal, dy),
            (float) Math.atan2(-dx, -dz), distance / HAND_REACH_PIXELS);
    }
}
