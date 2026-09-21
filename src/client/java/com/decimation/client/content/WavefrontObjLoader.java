package com.decimation.client.content;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public final class WavefrontObjLoader {
    private WavefrontObjLoader() { }

    public static ObjModel load(Reader input) throws IOException {
        List<ObjModel.Vertex> vertices = new ArrayList<>();
        List<ObjModel.TextureCoordinate> textureCoordinates = new ArrayList<>();
        List<ObjModel.Face> faces = new ArrayList<>();
        String object = "default";

        try (BufferedReader reader = new BufferedReader(input)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] fields = line.split("\\s+");
                switch (fields[0]) {
                    case "o", "g" -> object = fields.length > 1 ? fields[1] : "unnamed";
                    case "v" -> vertices.add(new ObjModel.Vertex(
                        Float.parseFloat(fields[1]), Float.parseFloat(fields[2]), Float.parseFloat(fields[3])));
                    case "vt" -> textureCoordinates.add(new ObjModel.TextureCoordinate(
                        Float.parseFloat(fields[1]), Float.parseFloat(fields[2])));
                    case "f" -> faces.add(parseFace(object, fields, vertices.size(), textureCoordinates.size()));
                    default -> { }
                }
            }
        }
        return new ObjModel(vertices, textureCoordinates, faces);
    }

    private static ObjModel.Face parseFace(String object, String[] fields, int vertexCount, int uvCount) {
        int[] vertices = new int[fields.length - 1];
        int[] uvs = new int[fields.length - 1];
        for (int i = 1; i < fields.length; i++) {
            String[] indices = fields[i].split("/", -1);
            vertices[i - 1] = resolveIndex(Integer.parseInt(indices[0]), vertexCount);
            uvs[i - 1] = indices.length > 1 && !indices[1].isEmpty()
                ? resolveIndex(Integer.parseInt(indices[1]), uvCount) : -1;
        }
        return new ObjModel.Face(object, vertices, uvs);
    }

    private static int resolveIndex(int index, int size) {
        int resolved = index > 0 ? index - 1 : size + index;
        if (resolved < 0 || resolved >= size) throw new IllegalArgumentException("OBJ index out of range");
        return resolved;
    }
}

