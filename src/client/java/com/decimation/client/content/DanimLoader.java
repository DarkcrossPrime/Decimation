package com.decimation.client.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;

public final class DanimLoader {
    private DanimLoader() { }

    public static DanimAnimation load(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        if (!"decimation:danim".equals(root.get("format").getAsString())) {
            throw new IllegalArgumentException("Unsupported DANIM format");
        }
        return new DanimAnimation(root.get("version").getAsInt(), root.get("length").getAsInt(),
            root.has("static") && root.get("static").getAsBoolean(), root.get("hand").getAsInt(), root);
    }
}

