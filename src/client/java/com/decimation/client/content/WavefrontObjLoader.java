package com.decimation.client.content;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/** Resource-prepare parser; OBJ objects and coordinates retain their original meaning. */
public final class WavefrontObjLoader {
    private static final int MAX_ELEMENTS = 200_000;
    private WavefrontObjLoader() { }

    public static ObjModel load(Reader input) throws IOException {
        List<ObjModel.Vertex> vertices = new ArrayList<>();
        List<ObjModel.TextureCoordinate> uvs = new ArrayList<>();
        List<ObjModel.Face> faces = new ArrayList<>();
        String object = "default";
        int lineNumber = 0;
        try (BufferedReader reader = new BufferedReader(input)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                int comment = line.indexOf('#');
                if (comment >= 0) line = line.substring(0, comment);
                line = line.strip();
                if (line.isEmpty()) continue;
                String[] fields = line.split("\\s+");
                switch (fields[0]) {
                    case "o", "g" -> object = fields.length > 1 ? fields[1] : "unnamed";
                    case "v" -> vertices.add(new ObjModel.Vertex(number(fields[1]), number(fields[2]), number(fields[3])));
                    case "vt" -> uvs.add(new ObjModel.TextureCoordinate(number(fields[1]), number(fields[2])));
                    case "f" -> {
                        if (fields.length < 4 || fields.length > 65) throw new IllegalArgumentException("face needs 3..64 corners");
                        int[] positions = new int[fields.length - 1], textures = new int[positions.length];
                        for (int i = 1; i < fields.length; i++) {
                            String[] indices = fields[i].split("/", -1);
                            positions[i - 1] = index(indices[0], vertices.size());
                            textures[i - 1] = indices.length > 1 && !indices[1].isEmpty() ? index(indices[1], uvs.size()) : -1;
                        }
                        faces.add(new ObjModel.Face(object, positions, textures));
                    }
                    default -> { } // Materials/normals are replaced by the weapon texture and baked polygon normals.
                }
                if (vertices.size() > MAX_ELEMENTS || uvs.size() > MAX_ELEMENTS || faces.size() > MAX_ELEMENTS) {
                    throw new IllegalArgumentException("weapon OBJ exceeds element limit");
                }
            }
        } catch (IllegalArgumentException | IndexOutOfBoundsException failure) {
            throw new IOException("Invalid OBJ at line " + lineNumber + ": " + failure.getMessage(), failure);
        }
        if (vertices.isEmpty() || faces.isEmpty()) throw new IOException("Weapon OBJ has no geometry");
        return new ObjModel(vertices, uvs, faces);
    }

    private static float number(String text) {
        float value = Float.parseFloat(text);
        if (!Float.isFinite(value)) throw new IllegalArgumentException("non-finite coordinate");
        return value;
    }

    private static int index(String text, int size) {
        int index = Integer.parseInt(text);
        long resolved = index > 0 ? (long) index - 1 : (long) size + index;
        if (index == 0 || resolved < 0 || resolved >= size) throw new IllegalArgumentException("OBJ index out of range");
        return (int) resolved;
    }
}
