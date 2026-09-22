package com.decimation.module.gun.data;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Identifier;

public record WeaponAudio(WeaponSound shot, float distantThreshold,
                          Map<WeaponSound, Identifier> sounds,
                          List<WeaponSoundCue> reloadCues) {
    public WeaponAudio {
        if (distantThreshold < 0) throw new IllegalArgumentException("distant threshold cannot be negative");
        EnumMap<WeaponSound, Identifier> immutableSounds = new EnumMap<>(WeaponSound.class);
        immutableSounds.putAll(sounds);
        sounds = Collections.unmodifiableMap(immutableSounds);
        reloadCues = reloadCues.stream().sorted((left, right) -> Integer.compare(left.tick(), right.tick())).toList();
        if (!sounds.containsKey(shot)) throw new IllegalArgumentException("missing primary shot sound " + shot);
    }

    public Identifier sound(WeaponSound cue) {
        return sounds.get(cue);
    }

    public WeaponSound shotForDistance(double distance) {
        if (shot != WeaponSound.FIRE_SUPPRESSED
            && distance >= distantThreshold && sounds.containsKey(WeaponSound.FIRE_DISTANT)) {
            return WeaponSound.FIRE_DISTANT;
        }
        return shot;
    }
}
