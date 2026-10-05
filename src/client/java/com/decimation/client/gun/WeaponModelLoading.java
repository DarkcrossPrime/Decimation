package com.decimation.client.gun;

import com.decimation.Decimation;
import com.decimation.client.content.DanimFrames;
import com.decimation.client.content.WavefrontObjLoader;
import com.decimation.module.gun.GunModule;
import com.decimation.module.gun.data.WeaponDefinition;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/** Assets load with the model reload's own ResourceManager, including the active resource packs. */
public final class WeaponModelLoading {
    private WeaponModelLoading() { }
    public static void register() {
        PreparableModelLoadingPlugin.register((state, executor) -> CompletableFuture.supplyAsync(
            () -> prepare(state.resourceManager()), executor), (prepared, context) ->
            context.modifyItemModelAfterBake().register((original, bake) -> afterBake(prepared, original, bake)));
    }

    static ItemModel afterBake(Map<Identifier, WeaponVisualData> prepared, ItemModel original,
                               ModelModifier.AfterBakeItem.Context bake) {
        WeaponVisualData data = prepared.get(bake.itemId());
        if (data == null) return original;
        ItemModel model = new WeaponItemModel(original, data, bake.bakingContext().blockModelBaker().materials());
        Decimation.LOGGER.info("Bound animated OBJ item model: {}", bake.itemId());
        return model;
    }

    private static Map<Identifier, WeaponVisualData> prepare(ResourceManager resources) {
        Map<Identifier, WeaponVisualData> models = new LinkedHashMap<>();
        for (WeaponDefinition weapon : GunModule.catalog().definitions().values()) {
            try {
                var modelId = Identifier.parse(weapon.assets().model());
                var textureId = Identifier.parse(weapon.assets().texture());
                resources.getResourceOrThrow(textureId);
                DanimFrames fire = animation(resources, weapon.assets().fireAnimation());
                DanimFrames reload = animation(resources, weapon.assets().reloadAnimation());
                String rackId = weapon.assets().fireAnimation().replace("_fire.danim.json", "_rack.danim.json");
                DanimFrames rack = resources.getResource(Identifier.parse(rackId)).isPresent() ? animation(resources, rackId) : DanimFrames.empty();
                try (var reader = resources.getResourceOrThrow(modelId).openAsReader()) {
                    var obj = WavefrontObjLoader.load(reader);
                    var visual = WeaponVisualData.prepare(weapon, obj, fire, reload, rack);
                    var mesh = visual.mesh();
                    models.put(Identifier.parse(weapon.id()), visual);
                    Decimation.LOGGER.info("Baked {}: {} faces, {} draw groups, {} degenerate faces skipped",
                        weapon.id(), mesh.sourceFaces(), mesh.parts().size(), mesh.skippedFaces());
                }
            } catch (IOException failure) {
                throw new UncheckedIOException("Could not prepare weapon visuals: " + weapon.id(), failure);
            } catch (RuntimeException failure) {
                throw new IllegalArgumentException("Invalid weapon visuals: " + weapon.id(), failure);
            }
        }
        return Map.copyOf(models);
    }

    private static DanimFrames animation(ResourceManager resources, String id) throws IOException {
        try (var reader = resources.getResourceOrThrow(Identifier.parse(id)).openAsReader()) { return DanimFrames.load(reader); }
    }
}
