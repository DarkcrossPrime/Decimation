package com.decimation.client.gun;

import com.decimation.client.content.ObjModel;
import org.joml.Vector3f;

/** Immutable sight references baked from the reflected model, outside the frame loop. */
public record WeaponSightLine(Point rear, Point front, float eyeRelief) {
    public record Point(float x, float y, float z) {
        public Vector3f vector() { return new Vector3f(x, y, z); }
    }
    public static WeaponSightLine prepare(ObjModel model, String id) {
        if (id.equals("decimation:famas_custom")) {
            var optic = bounds(model, "scope");
            if (optic != null) {
                var rear = new Point(optic.minX, (optic.minY + optic.maxY) / 2, (optic.minZ + optic.maxZ) / 2);
                return new WeaponSightLine(rear, new Point(optic.maxX, rear.y, rear.z), .22f);
            }
        }
        boolean famas = id.equals("decimation:famas");
        var rear = famas ? bounds(model, "gunModel85", "gunModel86") : bounds(model, "defaultScopeModel2", "defaultScopeModel3");
        var front = famas ? bounds(model, "gunModel82") : bounds(model, "defaultScopeModel9");
        if (rear == null || front == null) return null;
        return new WeaponSightLine(new Point((rear.minX + rear.maxX) / 2, (rear.minY + rear.maxY) / 2, (rear.minZ + rear.maxZ) / 2),
            new Point((front.minX + front.maxX) / 2, front.maxY, (front.minZ + front.maxZ) / 2), .22f);
    }
    private record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) { }
    private static Bounds bounds(ObjModel model, String... names) {
        float minX = Float.POSITIVE_INFINITY, minY = minX, minZ = minX;
        float maxX = Float.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (var face : model.faces()) {
            boolean selected = false;
            for (String name : names) if (name.equals(face.object())) { selected = true;break; }
            if (!selected) continue;
            for (int index : face.vertexIndices()) {
                var vertex = model.vertices().get(index);
                minX = Math.min(minX, vertex.x());maxX = Math.max(maxX, vertex.x());
                minY = Math.min(minY, vertex.y());maxY = Math.max(maxY, vertex.y());
                minZ = Math.min(minZ, vertex.z());maxZ = Math.max(maxZ, vertex.z());
            }
        }
        return Float.isFinite(minX) ? new Bounds(minX, minY, minZ, maxX, maxY, maxZ) : null;
    }
}
