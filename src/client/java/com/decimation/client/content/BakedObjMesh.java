package com.decimation.client.content;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Indexed, flat-shaded quad geometry, grouped by identical animation tracks at bake time. */
public record BakedObjMesh(List<Part> parts, Bounds bounds, int sourceFaces, int skippedFaces) {
    public BakedObjMesh { parts = List.copyOf(parts); }

    public static BakedObjMesh bake(ObjModel model, DanimFrames fire, DanimFrames reload, DanimFrames rack) {
        return bake(model, fire, reload, rack, Map.of());
    }
    public static BakedObjMesh bake(ObjModel model, DanimFrames fire, DanimFrames reload, DanimFrames rack, Map<String, String> bindings) {
        Map<Tracks, Builder> groups = new LinkedHashMap<>();
        int skipped = 0;
        for (ObjModel.Face face : model.faces()) {
            int[] positions = face.vertexIndices();
            double nx = 0, ny = 0, nz = 0;
            for (int i = 0; i < positions.length; i++) {
                var a = model.vertices().get(positions[i]);
                var b = model.vertices().get(positions[(i + 1) % positions.length]);
                nx += (a.y() - b.y()) * (a.z() + b.z());
                ny += (a.z() - b.z()) * (a.x() + b.x());
                nz += (a.x() - b.x()) * (a.y() + b.y());
            }
            double magnitude = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (magnitude < 1.0e-12) { skipped++;continue; }
            if (!Double.isFinite(magnitude)) throw new IllegalArgumentException("invalid OBJ polygon normal");
            String assembly = bindings.getOrDefault(face.object(), face.object());
            Tracks tracks = new Tracks(fire.track(face.object(), assembly), reload.track(face.object(), assembly), rack.track(face.object(), assembly));
            Builder group = groups.computeIfAbsent(tracks, Builder::new);
            int[] corners = new int[positions.length];
            for (int corner = 0; corner < positions.length; corner++) {
                var vertex = model.vertices().get(positions[corner]);
                int texture = face.textureIndices()[corner];
                float u = texture < 0 ? 0 : model.textureCoordinates().get(texture).u();
                float v = texture < 0 ? 0 : 1 - model.textureCoordinates().get(texture).v();
                Vertex baked = new Vertex(vertex.x(), vertex.y(), vertex.z(), u, v,
                    (float) (nx / magnitude), (float) (ny / magnitude), (float) (nz / magnitude));
                corners[corner] = group.vertices.computeIfAbsent(baked, ignored -> group.vertices.size());
            }
            if (corners.length == 4) for (int corner : corners) group.indices.add(corner);
            else for (int i = 1; i < corners.length - 1; i++) {
                // Entity layers draw quads: repeat the final triangle corner, never straddle polygons.
                group.indices.add(corners[0]);group.indices.add(corners[i]);
                group.indices.add(corners[i + 1]);group.indices.add(corners[i + 1]);
            }
        }
        List<Part> parts = groups.values().stream().map(Builder::build).toList();
        if (parts.isEmpty()) throw new IllegalArgumentException("OBJ has no non-degenerate faces");
        float minX = Float.POSITIVE_INFINITY, minY = minX, minZ = minX;
        float maxX = Float.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (Part part : parts) for (Vertex vertex : part.vertices) {
            minX = Math.min(minX, vertex.x);minY = Math.min(minY, vertex.y);minZ = Math.min(minZ, vertex.z);
            maxX = Math.max(maxX, vertex.x);maxY = Math.max(maxY, vertex.y);maxZ = Math.max(maxZ, vertex.z);
        }
        return new BakedObjMesh(parts, new Bounds(minX, minY, minZ, maxX, maxY, maxZ), model.faces().size(), skipped);
    }

    public record Vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) { }
    public record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) { }
    public record Tracks(DanimFrames.Track fire, DanimFrames.Track reload, DanimFrames.Track rack) { }

    public static final class Part {
        private final List<Vertex> vertices;
        private final int[] indices;
        private final Tracks tracks;
        private Part(List<Vertex> vertices, int[] indices, Tracks tracks) {
            this.vertices = List.copyOf(vertices);this.indices = indices;this.tracks = tracks;
        }
        public List<Vertex> vertices() { return vertices; }
        public int cornerCount() { return indices.length; }
        public Vertex corner(int index) { return vertices.get(indices[index]); }
        public Tracks tracks() { return tracks; }
    }

    private static final class Builder {
        private final Map<Vertex, Integer> vertices = new LinkedHashMap<>();
        private final List<Integer> indices = new ArrayList<>();
        private final Tracks tracks;
        private Builder(Tracks tracks) { this.tracks = tracks; }
        private Part build() { return new Part(new ArrayList<>(vertices.keySet()), indices.stream().mapToInt(Integer::intValue).toArray(), tracks); }
    }
}
