package com.decimation.module.gun.data;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record WeaponAudio(WeaponSound shot, float distantThreshold,
                          Map<WeaponSound, String> sounds,
                          List<WeaponSoundCue> reloadCues) {
    public WeaponAudio {
        java.util.Objects.requireNonNull(shot, "shot");
        DefinitionValidation.finite(distantThreshold);
        if (distantThreshold < 0) throw new IllegalArgumentException("distant threshold cannot be negative");
        EnumMap<WeaponSound, String> immutableSounds = new EnumMap<>(WeaponSound.class);
        immutableSounds.putAll(sounds);
        immutableSounds.values().forEach(id -> DefinitionValidation.identifier(id, "sound event"));
        sounds = Collections.unmodifiableMap(immutableSounds);
        reloadCues = reloadCues.stream().sorted((left, right) -> Integer.compare(left.tick(), right.tick())).toList();
        if (!sounds.containsKey(shot)) throw new IllegalArgumentException("missing primary shot sound " + shot);
    }

    public String sound(WeaponSound cue) {
        return sounds.get(cue);
    }

    public WeaponSound shotForDistance(double distance) {
        if (shot != WeaponSound.FIRE_SUPPRESSED
            && distance >= distantThreshold && sounds.containsKey(WeaponSound.FIRE_DISTANT)) {
            return WeaponSound.FIRE_DISTANT;
        }
        return shot;
    }

    public static float volume(WeaponSound cue) {
        return switch (cue) {
            case FIRE_DISTANT -> 8;
            case FIRE -> 4;
            case FIRE_SUPPRESSED -> 2;
            default -> 1;
        };
    }

    public static double range(WeaponSound cue) { return 16 * volume(cue); }

    public double maximumShotRange() {
        return shot != WeaponSound.FIRE_SUPPRESSED && sounds.containsKey(WeaponSound.FIRE_DISTANT)
            ? Math.max(range(shot), range(WeaponSound.FIRE_DISTANT)) : range(shot);
    }
}
