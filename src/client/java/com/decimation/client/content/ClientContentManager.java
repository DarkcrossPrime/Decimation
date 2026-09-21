package com.decimation.client.content;

import com.decimation.Decimation;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

public final class ClientContentManager implements SimpleSynchronousResourceReloadListener {
    public static final ClientContentManager INSTANCE = new ClientContentManager();
    private static final Identifier INDEX = new Identifier(Decimation.MOD_ID, "content/index.json");
    private Map<Identifier, ObjModel> models = Map.of();
    private Map<Identifier, DanimAnimation> animations = Map.of();

    private ClientContentManager() { }

    @Override
    public Identifier getFabricId() {
        return new Identifier(Decimation.MOD_ID, "content");
    }

    @Override
    public void reload(ResourceManager manager) {
        Map<Identifier, ObjModel> loadedModels = new LinkedHashMap<>();
        Map<Identifier, DanimAnimation> loadedAnimations = new LinkedHashMap<>();
        try (Reader reader = manager.getResourceOrThrow(INDEX).getReader()) {
            JsonObject index = JsonParser.parseReader(reader).getAsJsonObject();
            loadModels(manager, index.getAsJsonArray("models"), loadedModels);
            loadAnimations(manager, index.getAsJsonArray("animations"), loadedAnimations);
        } catch (Exception exception) {
            Decimation.LOGGER.error("Failed to load Decimation content index", exception);
        }
        models = Collections.unmodifiableMap(loadedModels);
        animations = Collections.unmodifiableMap(loadedAnimations);
        Decimation.LOGGER.info("Loaded {} OBJ models and {} DANIM animations", models.size(), animations.size());
    }

    private static void loadModels(ResourceManager manager, JsonArray ids, Map<Identifier, ObjModel> output)
        throws IOException {
        for (var element : ids) {
            Identifier id = new Identifier(element.getAsString());
            Resource resource = manager.getResourceOrThrow(id);
            try (Reader reader = resource.getReader()) {
                output.put(id, WavefrontObjLoader.load(reader));
            }
        }
    }

    private static void loadAnimations(ResourceManager manager, JsonArray ids,
                                       Map<Identifier, DanimAnimation> output) throws IOException {
        for (var element : ids) {
            Identifier id = new Identifier(element.getAsString());
            Resource resource = manager.getResourceOrThrow(id);
            try (Reader reader = resource.getReader()) {
                output.put(id, DanimLoader.load(reader));
            }
        }
    }

    public Map<Identifier, ObjModel> models() { return models; }
    public Map<Identifier, DanimAnimation> animations() { return animations; }
}
