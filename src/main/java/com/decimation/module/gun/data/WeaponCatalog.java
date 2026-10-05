package com.decimation.module.gun.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Small, immutable startup catalogue. Does not load geometry or touch registries. */
public final class WeaponCatalog {
    public static final String RESOURCE = "data/decimation/weapons/index.json";
    private final Map<String, WeaponDefinition> definitions;
    private final Map<String, AmmunitionDefinition> ammunition;
    private final String fingerprint;

    private WeaponCatalog(Map<String, WeaponDefinition> definitions,
                          Map<String, AmmunitionDefinition> ammunition, String fingerprint) {
        this.definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
        this.ammunition = Collections.unmodifiableMap(new LinkedHashMap<>(ammunition));
        this.fingerprint = fingerprint;
    }

    public static WeaponCatalog load(ClassLoader loader) {
        try (InputStream stream = loader.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IOException("missing generated " + RESOURCE);
            WeaponCatalog catalog = read(new InputStreamReader(stream, StandardCharsets.UTF_8));
            // Verify only resources needed by this catalogue; never parse OBJ/DANIM here.
            for (WeaponDefinition definition : catalog.definitions.values()) {
                WeaponAssets assets = definition.assets();
                for (String id : List.of(assets.model(), assets.texture(), assets.itemTexture(),
                                        assets.fireAnimation(), assets.reloadAnimation())) {
                    requireResource(loader, id);
                }
            }
            for (String id : catalog.ammunition.keySet()) {
                requireResource(loader, "decimation:textures/item/" + id.substring(id.indexOf(':') + 1) + ".png");
            }
            return catalog;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Cannot load Decimation weapon catalog: " + exception.getMessage(), exception);
        }
    }

    private static void requireResource(ClassLoader loader, String id) throws IOException {
        int colon = id.indexOf(':');
        String path = "assets/" + id.substring(0, colon) + "/" + id.substring(colon + 1);
        if (loader.getResource(path) == null) throw new IOException("missing weapon resource " + path);
    }

    public static WeaponCatalog read(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        if (!"decimation:weapon_catalog".equals(string(root, "format")) || integer(root, "version") != 2) {
            throw new IllegalArgumentException("unsupported weapon catalog format/version");
        }
        Map<String, WeaponDefinition> values = new LinkedHashMap<>();
        for (JsonElement element : array(root, "weapons")) {
            JsonObject json = element.getAsJsonObject();
            String id = localId(string(json, "registry_id"));
            try {
                WeaponDefinition definition = parse(json, id);
                if (values.putIfAbsent(id, definition) != null) {
                    throw new IllegalArgumentException("duplicate weapon id");
                }
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("weapon " + id + ": " + exception.getMessage(), exception);
            }
        }
        if (values.isEmpty()) throw new IllegalArgumentException("weapon catalog is empty");
        Map<String, AmmunitionDefinition> ammo = new LinkedHashMap<>();
        for (JsonElement element : array(root, "ammunition")) {
            JsonObject json = element.getAsJsonObject();
            AmmunitionDefinition definition = new AmmunitionDefinition(localId(string(json, "registry_id")),
                string(json, "display_name"), integer(json, "max_stack_size"));
            if (ammo.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalArgumentException("duplicate ammunition " + definition.id());
            }
            if (values.containsKey(definition.id())) {
                throw new IllegalArgumentException("weapon/ammunition id collision: " + definition.id());
            }
        }
        for (WeaponDefinition definition : values.values()) {
            if (!ammo.containsKey(definition.ammo().itemId())) {
                throw new IllegalArgumentException("weapon " + definition.id() + ": missing ammunition " + definition.ammo().itemId());
            }
        }
        try {
            String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(root.toString().getBytes(StandardCharsets.UTF_8)));
            return new WeaponCatalog(values, ammo, fingerprint);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static WeaponDefinition parse(JsonObject json, String id) {
        JsonObject ammoJson = object(json, "ammo");
        AmmoDefinition ammo = new AmmoDefinition(localId(string(ammoJson, "item")),
            integer(ammoJson, "capacity"), integer(ammoJson, "chamber_capacity"),
            ammoJson.has("spawn_loaded") && bool(ammoJson, "spawn_loaded"));
        List<FireMode> modes = new ArrayList<>();
        for (JsonElement mode : array(json, "fire_modes")) modes.add(FireMode.parse(stringValue(mode, "fire_modes")));
        JsonObject b = object(json, "ballistics");
        BallisticsDefinition ballistics = new BallisticsDefinition(
            number(b, "damage"), doubleNumber(b, "range"), doubleNumber(b, "falloff_start"),
            number(b, "minimum_multiplier"), number(b, "head_multiplier"), integer(b, "penetration_count"),
            number(b, "penetration_retention"), optionalNumber(b, "projectile_speed", 0),
            optionalNumber(b, "projectile_divergence", 0));
        JsonObject h = object(json, "handling");
        HandlingDefinition handling = new HandlingDefinition(number(h, "hip_spread"), number(h, "ads_spread"),
            integer(h, "ads_ticks"), number(h, "recoil_pitch"), number(h, "recoil_yaw"));
        WeaponPresentation presentation = parsePresentation(object(json, "presentation"));
        JsonObject a = object(json, "assets");
        WeaponAssets assets = new WeaponAssets(string(a, "model"), string(a, "texture"), string(a, "item_texture"),
            string(a, "fire_animation"), string(a, "reload_animation"));
        JsonObject audioJson = object(json, "audio");
        Map<WeaponSound, String> sounds = new EnumMap<>(WeaponSound.class);
        for (Map.Entry<String, JsonElement> entry : object(audioJson, "sounds").entrySet()) {
            sounds.put(WeaponSound.parse(entry.getKey()), stringValue(entry.getValue(), "sound " + entry.getKey()));
        }
        List<WeaponSoundCue> cues = new ArrayList<>();
        for (JsonElement element : array(audioJson, "reload_cues")) {
            JsonObject cue = element.getAsJsonObject();
            cues.add(new WeaponSoundCue(integer(cue, "tick"), WeaponSound.parse(string(cue, "sound"))));
        }
        WeaponAudio audio = new WeaponAudio(WeaponSound.parse(string(audioJson, "shot")),
            number(audioJson, "distant_threshold"), sounds, cues);
        return new WeaponDefinition(id, string(json, "content_id"), string(json, "display_name"),
            WeaponMechanism.parse(string(json, "mechanism")), ammo, modes,
            json.has("burst_size") ? integer(json, "burst_size") : 1,
            integer(json, "rate_of_fire"), integer(json, "reload_ticks"), ballistics, handling, presentation, assets, audio);
    }

    private static WeaponPresentation parsePresentation(JsonObject json) {
        JsonObject firstPerson = object(json, "first_person");
        JsonObject hip = object(firstPerson, "hip");
        JsonObject ads = object(firstPerson, "ads");
        JsonObject sprint = firstPerson.has("sprint") ? object(firstPerson, "sprint") : hip;
        return new WeaponPresentation(parseTransform(hip), parseTransform(ads), parseTransform(sprint),
            parseArmPose(object(hip, "arms")), parseArmPose(object(ads, "arms")),
            parseArmPose(object(sprint, "arms")), parseTransform(object(json, "third_person")));
    }

    private static WeaponArmPose parseArmPose(JsonObject json) {
        float[] main = vector(json, "main_hand");
        float[] off = vector(json, "off_hand");
        return new WeaponArmPose(new ArmRotation(main[0], main[1], main[2]), new ArmRotation(off[0], off[1], off[2]));
    }

    private static WeaponTransform parseTransform(JsonObject json) {
        float[] position = vector(json, "translation");
        float[] rotation = vector(json, "rotation");
        return new WeaponTransform(position[0], position[1], position[2], rotation[0], rotation[1], rotation[2], number(json, "scale"));
    }

    private static float[] vector(JsonObject json, String name) {
        JsonArray values = array(json, name);
        if (values.size() != 3) throw new IllegalArgumentException(name + " must contain three numbers");
        return new float[] {numberValue(values.get(0), name), numberValue(values.get(1), name), numberValue(values.get(2), name)};
    }

    private static JsonElement field(JsonObject json, String name) {
        JsonElement value = json.get(name);
        if (value == null || value.isJsonNull()) throw new IllegalArgumentException("missing " + name);
        return value;
    }

    private static JsonObject object(JsonObject json, String name) {
        JsonElement value = field(json, name);
        if (!value.isJsonObject()) throw new IllegalArgumentException(name + " must be an object");
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonObject json, String name) {
        JsonElement value = field(json, name);
        if (!value.isJsonArray()) throw new IllegalArgumentException(name + " must be an array");
        return value.getAsJsonArray();
    }

    private static String string(JsonObject json, String name) { return stringValue(field(json, name), name); }

    private static String stringValue(JsonElement value, String name) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank()) {
            throw new IllegalArgumentException(name + " must be a nonempty string");
        }
        return value.getAsString();
    }

    private static int integer(JsonObject json, String name) {
        JsonElement value = field(json, name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        try { return value.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a 32-bit integer", exception);
        }
    }

    private static boolean bool(JsonObject json, String name) {
        JsonElement value = field(json, name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(name + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static float number(JsonObject json, String name) { return numberValue(field(json, name), name); }

    private static double doubleNumber(JsonObject json, String name) {
        JsonElement value = field(json, name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(name + " must be a number");
        }
        double result = value.getAsDouble();
        if (!Double.isFinite(result)) throw new IllegalArgumentException(name + " must be finite");
        return result;
    }

    private static float numberValue(JsonElement value, String name) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(name + " must be a number");
        }
        float result = value.getAsFloat();
        if (!Float.isFinite(result)) throw new IllegalArgumentException(name + " must be finite");
        return result;
    }

    private static float optionalNumber(JsonObject json, String name, float fallback) {
        return json.has(name) ? number(json, name) : fallback;
    }

    private static String localId(String path) {
        DefinitionValidation.path(path, "registry id");
        return "decimation:" + path;
    }

    public Map<String, WeaponDefinition> definitions() { return definitions; }
    public Map<String, AmmunitionDefinition> ammunition() { return ammunition; }
    public WeaponDefinition get(String id) { return definitions.get(id); }
    public String fingerprint() { return fingerprint; }
}
