package com.decimation.client.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DanimLoader {
    private DanimLoader() { }

    public static DanimAnimation load(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        if (!"decimation:danim".equals(root.get("format").getAsString())) {
            throw new IllegalArgumentException("Unsupported DANIM format");
        }
        Map<String, List<DanimAnimation.Keyframe>> tracks = new LinkedHashMap<>();
        for (JsonElement frameElement : root.getAsJsonArray("frames")) {
            JsonObject frame = frameElement.getAsJsonObject();
            if (!frame.has("transforms")) continue;
            int index = frame.get("index").getAsInt();
            for (Map.Entry<String, JsonElement> entry : frame.getAsJsonObject("transforms").entrySet()) {
                JsonObject transform = entry.getValue().getAsJsonObject();
                tracks.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).add(
                    new DanimAnimation.Keyframe(index, vector(transform, "position"),
                        vector(transform, "rotation")));
            }
        }
        return new DanimAnimation(root.get("version").getAsInt(), root.get("length").getAsInt(),
            root.has("static") && root.get("static").getAsBoolean(), root.get("hand").getAsInt(), tracks);
    }

    private static float[] vector(JsonObject json, String name) {
        if (!json.has(name)) return new float[] {0, 0, 0};
        JsonArray values = json.getAsJsonArray(name);
        return new float[] {values.get(0).getAsFloat(), values.get(1).getAsFloat(),
            values.get(2).getAsFloat()};
    }
}
