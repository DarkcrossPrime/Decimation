package com.decimation.client.gun;

import com.decimation.client.content.DanimAnimation;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class DanimSampler {
    private DanimSampler() { }

    public static Transform sample(DanimAnimation animation, String object, int requestedFrame) {
        JsonArray frames = animation.data().getAsJsonArray("frames");
        int frame = Math.min(Math.max(requestedFrame, 0), frames.size() - 1);
        for (int index = frame; index >= 0; index--) {
            JsonObject value = frames.get(index).getAsJsonObject();
            if (!value.has("transforms")) continue;
            JsonObject transforms = value.getAsJsonObject("transforms");
            JsonElement candidate = transforms.get(object);
            if (candidate == null && "default".equals(object)) candidate = transforms.get("Model");
            if (candidate == null) continue;
            JsonObject transform = candidate.getAsJsonObject();
            return new Transform(vector(transform, "position"), vector(transform, "rotation"));
        }
        return Transform.IDENTITY;
    }

    private static float[] vector(JsonObject json, String name) {
        if (!json.has(name)) return new float[] {0, 0, 0};
        JsonArray values = json.getAsJsonArray(name);
        return new float[] {values.get(0).getAsFloat(), values.get(1).getAsFloat(), values.get(2).getAsFloat()};
    }

    public record Transform(float[] position, float[] rotation) {
        public static final Transform IDENTITY = new Transform(new float[] {0, 0, 0}, new float[] {0, 0, 0});
    }
}
