package com.decimation.module.gun.data;

import com.decimation.Decimation;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Identifier;

public final class WeaponCatalog {
    private static final String RESOURCE = "data/decimation/weapons/index.json";
    private final Map<Identifier, WeaponDefinition> definitions;

    private WeaponCatalog(Map<Identifier, WeaponDefinition> definitions) {
        this.definitions = Collections.unmodifiableMap(definitions);
    }

    public static WeaponCatalog load() {
        ClassLoader loader = WeaponCatalog.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IOException("missing " + RESOURCE);
            JsonObject root = JsonParser.parseReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!"decimation:weapon_catalog".equals(root.get("format").getAsString())
                || root.get("version").getAsInt() != 1) {
                throw new IOException("unsupported weapon catalog format");
            }
            Map<Identifier, WeaponDefinition> values = new LinkedHashMap<>();
            for (JsonElement element : root.getAsJsonArray("weapons")) {
                WeaponDefinition definition = parse(element.getAsJsonObject());
                if (values.putIfAbsent(definition.id(), definition) != null) {
                    throw new IOException("duplicate weapon " + definition.id());
                }
            }
            if (values.isEmpty()) throw new IOException("weapon catalog is empty");
            return new WeaponCatalog(values);
        } catch (RuntimeException | IOException exception) {
            throw new IllegalStateException("Cannot load Decimation weapon catalog", exception);
        }
    }

    private static WeaponDefinition parse(JsonObject json) {
        Identifier id = new Identifier(Decimation.MOD_ID, json.get("registry_id").getAsString());
        JsonObject ammoJson = json.getAsJsonObject("ammo");
        AmmoDefinition ammo = new AmmoDefinition(
            new Identifier(Decimation.MOD_ID, ammoJson.get("item").getAsString()),
            ammoJson.get("capacity").getAsInt(), ammoJson.get("chamber_capacity").getAsInt(),
            ammoJson.has("spawn_loaded") && ammoJson.get("spawn_loaded").getAsBoolean());

        List<FireMode> modes = new ArrayList<>();
        for (JsonElement mode : json.getAsJsonArray("fire_modes")) modes.add(FireMode.parse(mode.getAsString()));
        JsonObject ballisticsJson = json.getAsJsonObject("ballistics");
        BallisticsDefinition ballistics = new BallisticsDefinition(
            ballisticsJson.get("damage").getAsFloat(), ballisticsJson.get("range").getAsDouble(),
            ballisticsJson.get("falloff_start").getAsDouble(),
            ballisticsJson.get("minimum_multiplier").getAsFloat(),
            ballisticsJson.get("head_multiplier").getAsFloat(),
            ballisticsJson.get("penetration_count").getAsInt(),
            ballisticsJson.get("penetration_retention").getAsFloat(),
            optionalFloat(ballisticsJson, "projectile_speed", 0),
            optionalFloat(ballisticsJson, "projectile_divergence", 0));
        JsonObject handlingJson = json.getAsJsonObject("handling");
        HandlingDefinition handling = new HandlingDefinition(
            handlingJson.get("hip_spread").getAsFloat(), handlingJson.get("ads_spread").getAsFloat(),
            handlingJson.get("ads_ticks").getAsInt(), handlingJson.get("recoil_pitch").getAsFloat(),
            handlingJson.get("recoil_yaw").getAsFloat());
        WeaponPresentation presentation = parsePresentation(json.getAsJsonObject("presentation"));
        JsonObject assetsJson = json.getAsJsonObject("assets");
        WeaponAssets assets = new WeaponAssets(identifier(assetsJson, "model"),
            identifier(assetsJson, "texture"), identifier(assetsJson, "item_texture"),
            identifier(assetsJson, "fire_animation"), identifier(assetsJson, "reload_animation"));
        JsonObject audioJson = json.getAsJsonObject("audio");
        Map<WeaponSound, Identifier> sounds = new EnumMap<>(WeaponSound.class);
        for (Map.Entry<String, JsonElement> entry : audioJson.getAsJsonObject("sounds").entrySet()) {
            sounds.put(WeaponSound.parse(entry.getKey()), new Identifier(entry.getValue().getAsString()));
        }
        List<WeaponSoundCue> reloadCues = new ArrayList<>();
        for (JsonElement element : audioJson.getAsJsonArray("reload_cues")) {
            JsonObject cue = element.getAsJsonObject();
            reloadCues.add(new WeaponSoundCue(cue.get("tick").getAsInt(),
                WeaponSound.parse(cue.get("sound").getAsString())));
        }
        WeaponAudio audio = new WeaponAudio(WeaponSound.parse(audioJson.get("shot").getAsString()),
            audioJson.get("distant_threshold").getAsFloat(), sounds, reloadCues);
        return new WeaponDefinition(id, json.get("content_id").getAsString(),
            json.get("display_name").getAsString(), WeaponMechanism.parse(json.get("mechanism").getAsString()),
            ammo, modes, json.has("burst_size") ? json.get("burst_size").getAsInt() : 1,
            json.get("rate_of_fire").getAsInt(), json.get("reload_ticks").getAsInt(),
            ballistics, handling, presentation, assets, audio);
    }

    private static WeaponPresentation parsePresentation(JsonObject json) {
        if (json == null || !json.has("first_person")) {
            WeaponTransform hip = new WeaponTransform(0.48f, 0.50f, 0.50f, 0, 0, 0, 0.025f);
            WeaponTransform ads = new WeaponTransform(0.48f, 0.46f, 0.46f, 0, 0, 0, 0.025f);
            WeaponTransform thirdPerson = new WeaponTransform(0.5f, 0.5f, 0.5f, 0, 0, 0, 0.025f);
            WeaponArmPose hipArms = new WeaponArmPose(
                new ArmRotation(-98, -14, 0), new ArmRotation(-103, 30, 0));
            WeaponArmPose adsArms = new WeaponArmPose(
                new ArmRotation(-104, -17.19f, 0), new ArmRotation(-107, 34.38f, 0));
            return new WeaponPresentation(hip, ads, hip, hipArms, adsArms, hipArms, thirdPerson);
        }
        JsonObject firstPerson = json.getAsJsonObject("first_person");
        JsonObject hip = firstPerson.getAsJsonObject("hip");
        JsonObject ads = firstPerson.getAsJsonObject("ads");
        JsonObject sprint = firstPerson.has("sprint") ? firstPerson.getAsJsonObject("sprint") : hip;
        return new WeaponPresentation(
            parseTransform(hip), parseTransform(ads), parseTransform(sprint),
            parseArmPose(hip.getAsJsonObject("arms")),
            parseArmPose(ads.getAsJsonObject("arms")),
            parseArmPose(sprint.getAsJsonObject("arms")),
            parseTransform(json.getAsJsonObject("third_person")));
    }

    private static WeaponArmPose parseArmPose(JsonObject json) {
        return new WeaponArmPose(
            parseArmRotation(json.getAsJsonArray("main_hand")),
            parseArmRotation(json.getAsJsonArray("off_hand")));
    }

    private static ArmRotation parseArmRotation(JsonArray json) {
        if (json == null || json.size() != 3) {
            throw new IllegalArgumentException("weapon arm rotations must contain three values");
        }
        return new ArmRotation(json.get(0).getAsFloat(), json.get(1).getAsFloat(),
            json.get(2).getAsFloat());
    }

    private static WeaponTransform parseTransform(JsonObject json) {
        JsonArray translation = json.getAsJsonArray("translation");
        JsonArray rotation = json.getAsJsonArray("rotation");
        if (translation.size() != 3 || rotation.size() != 3) {
            throw new IllegalArgumentException("weapon presentation vectors must contain three values");
        }
        return new WeaponTransform(
            translation.get(0).getAsFloat(), translation.get(1).getAsFloat(),
            translation.get(2).getAsFloat(), rotation.get(0).getAsFloat(),
            rotation.get(1).getAsFloat(), rotation.get(2).getAsFloat(),
            json.get("scale").getAsFloat());
    }

    private static float optionalFloat(JsonObject json, String name, float fallback) {
        return json.has(name) ? json.get(name).getAsFloat() : fallback;
    }

    private static Identifier identifier(JsonObject json, String name) {
        return new Identifier(json.get(name).getAsString());
    }

    public Map<Identifier, WeaponDefinition> definitions() {
        return definitions;
    }

    public WeaponDefinition get(Identifier id) {
        return definitions.get(id);
    }
}
