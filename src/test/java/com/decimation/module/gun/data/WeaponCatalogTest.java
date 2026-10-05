package com.decimation.module.gun.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.Consumer;

/** Catalogue contracts can be checked without bootstrapping Minecraft. */
public final class WeaponCatalogTest {
    private static JsonObject source;
    private static int rejected;

    public static void main(String[] args) throws Exception {
        ClassLoader loader = WeaponCatalogTest.class.getClassLoader();
        try (var stream = loader.getResourceAsStream(WeaponCatalog.RESOURCE)) {
            require(stream != null, "generated catalogue missing");
            source = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        WeaponCatalog catalog = WeaponCatalog.load(loader);
        require(catalog.definitions().size() == 4, "expected four source weapons");
        require(catalog.ammunition().size() == 3, "shared FAMAS ammo must be deduplicated");
        WeaponDefinition famas = catalog.get("decimation:famas");
        WeaponDefinition custom = catalog.get("decimation:famas_custom");
        require(famas.ammo().itemId().equals(custom.ammo().itemId()), "FAMAS variants must share ammo identity");
        require(famas.ammo().capacity() == 30 && custom.ammo().capacity() == 32, "capacities must remain weapon-specific");
        require(custom.presentation().firstPersonSprint().equals(custom.presentation().firstPersonHip()), "optional sprint must fall back to hip");
        require(catalog.get("decimation:crossbow").ammo().capacity() == 0, "crossbow magazine must remain absent");
        require(catalog.get("decimation:honeybadger").audio().shot() == WeaponSound.FIRE_SUPPRESSED, "suppressed audio metadata changed");
        require(catalog.ammunition().values().stream().allMatch(ammo -> ammo.maxStackSize() == 16), "legacy ammo stack size changed");

        reject(root -> root.addProperty("version", 1));
        reject(root -> root.getAsJsonArray("weapons").add(weapon(root).deepCopy()));
        reject(root -> weapon(root).addProperty("registry_id", "Bad ID"));
        reject(root -> weapon(root).addProperty("registry_id", "../escape"));
        reject(root -> weapon(root).addProperty("rate_of_fire", 1.5));
        reject(root -> weapon(root).addProperty("reload_ticks", 2147483648L));
        reject(root -> weapon(root).getAsJsonObject("ammo").addProperty("spawn_loaded", "true"));
        reject(root -> weapon(root).getAsJsonObject("ammo").addProperty("item", "missing_ammunition"));
        reject(root -> weapon(root).getAsJsonArray("fire_modes").add("semi"));
        reject(root -> weapon(root).getAsJsonObject("ballistics").addProperty("damage", 1e100));
        reject(root -> weapon(root).getAsJsonObject("ballistics").addProperty("projectile_speed", 0));
        reject(root -> weapon(root).getAsJsonObject("presentation").getAsJsonObject("first_person")
            .getAsJsonObject("hip").getAsJsonArray("translation").remove(0));
        reject(root -> root.getAsJsonArray("ammunition").get(0).getAsJsonObject()
            .addProperty("registry_id", weapon(root).get("registry_id").getAsString()));
        reject(root -> root.getAsJsonArray("ammunition").get(0).getAsJsonObject().addProperty("max_stack_size", 0));
        reject(root -> weapon(root).getAsJsonObject("audio").getAsJsonObject("sounds").remove("fire"));
        try {
            catalog.definitions().clear();
            throw new AssertionError("catalogue must be immutable");
        } catch (UnsupportedOperationException expected) { }

        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            require(FireMode.parse("semi") == FireMode.SEMI, "fire-mode parsing must not depend on locale");
            require(WeaponMechanism.parse("hitscan") == WeaponMechanism.HITSCAN, "mechanism parsing must not depend on locale");
        } finally { Locale.setDefault(previous); }

        ClassLoader missing = new ClassLoader(loader) {
            @Override public URL getResource(String name) {
                return name.endsWith("models/crossbow.obj") ? null : super.getResource(name);
            }
        };
        try {
            WeaponCatalog.load(missing);
            throw new AssertionError("missing model must fail before item registration");
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains("crossbow.obj"), "missing resource diagnostic needs its path");
        }
        System.out.println("Weapon catalogue checks passed: 4 weapons, 3 ammunition items, " + rejected + " invalid catalogues rejected.");
    }

    private static JsonObject weapon(JsonObject root) { return root.getAsJsonArray("weapons").get(0).getAsJsonObject(); }

    private static void reject(Consumer<JsonObject> change) {
        JsonObject root = source.deepCopy();
        change.accept(root);
        try {
            WeaponCatalog.read(new StringReader(root.toString()));
            throw new AssertionError("invalid catalogue accepted: " + root);
        } catch (IllegalArgumentException expected) { rejected++; }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
