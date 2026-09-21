package com.decimation.client.content;

import java.util.List;

public record ObjModel(List<Vertex> vertices, List<TextureCoordinate> textureCoordinates,
                       List<Face> faces) {
    public ObjModel {
        vertices = List.copyOf(vertices);
        textureCoordinates = List.copyOf(textureCoordinates);
        faces = List.copyOf(faces);
    }

    public record Vertex(float x, float y, float z) { }
    public record TextureCoordinate(float u, float v) { }
    public record Face(String object, int[] vertexIndices, int[] textureIndices) {
        public Face {
            vertexIndices = vertexIndices.clone();
            textureIndices = textureIndices.clone();
        }
    }
}

