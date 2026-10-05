package com.decimation.module.gun;

import com.decimation.Decimation;
import com.decimation.module.gun.data.WeaponCatalog;
import com.decimation.module.gun.data.WeaponDefinition;
import com.decimation.module.gun.data.WeaponSound;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Registry events and per-weapon cue references are resolved once at startup. */
public final class WeaponSounds {
    private static Map<Identifier, Map<WeaponSound, SoundEvent>> sounds = Map.of();
    private WeaponSounds() { }

    public static void initialize(WeaponCatalog catalog) {
        Map<String, Identifier> ids = new LinkedHashMap<>();
        for (WeaponDefinition weapon : catalog.definitions().values()) {
            for (String id : weapon.audio().sounds().values()) ids.computeIfAbsent(id, Identifier::parse);
        }
        for (Identifier id : ids.values()) {
            if (Decimation.MOD_ID.equals(id.getNamespace()) && BuiltInRegistries.SOUND_EVENT.containsKey(id)) {
                throw new IllegalStateException("Weapon sound already registered: " + id);
            }
            if (!Decimation.MOD_ID.equals(id.getNamespace()) && !BuiltInRegistries.SOUND_EVENT.containsKey(id)) {
                throw new IllegalStateException("Unknown external weapon sound: " + id);
            }
        }
        Map<String, SoundEvent> events = new LinkedHashMap<>();
        ids.forEach((text, id) -> events.put(text, Decimation.MOD_ID.equals(id.getNamespace())
            ? Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id))
            : BuiltInRegistries.SOUND_EVENT.getValue(id)));
        Map<Identifier, Map<WeaponSound, SoundEvent>> byWeapon = new LinkedHashMap<>();
        for (WeaponDefinition weapon : catalog.definitions().values()) {
            EnumMap<WeaponSound, SoundEvent> cues = new EnumMap<>(WeaponSound.class);
            weapon.audio().sounds().forEach((cue, id) -> cues.put(cue, events.get(id)));
            byWeapon.put(Identifier.parse(weapon.id()), Collections.unmodifiableMap(cues));
        }
        sounds = Collections.unmodifiableMap(byWeapon);
        Decimation.LOGGER.info("Registered {} weapon sound events", events.size());
    }

    public static SoundEvent get(Identifier weapon, WeaponSound cue) {
        Map<WeaponSound, SoundEvent> cues = sounds.get(weapon);
        return cues == null ? null : cues.get(cue);
    }
}
