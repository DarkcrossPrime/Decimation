package com.decimation.module.gun.data;

import java.util.List;

public record WeaponDefinition(String id, String contentId, String displayName,
                               WeaponMechanism mechanism, AmmoDefinition ammo,
                               List<FireMode> fireModes, int burstSize, int rateOfFire,
                               int reloadTicks, BallisticsDefinition ballistics,
                               HandlingDefinition handling, WeaponPresentation presentation,
                               WeaponAssets assets,
                               WeaponAudio audio) {
    public WeaponDefinition {
        DefinitionValidation.identifier(id, "weapon id");
        DefinitionValidation.path(contentId, "content id");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("missing weapon name");
        java.util.Objects.requireNonNull(mechanism, "mechanism");
        java.util.Objects.requireNonNull(ammo, "ammo");
        java.util.Objects.requireNonNull(ballistics, "ballistics");
        java.util.Objects.requireNonNull(handling, "handling");
        java.util.Objects.requireNonNull(presentation, "presentation");
        java.util.Objects.requireNonNull(assets, "assets");
        java.util.Objects.requireNonNull(audio, "audio");
        fireModes = List.copyOf(fireModes);
        if (fireModes.isEmpty() || rateOfFire <= 0 || reloadTicks <= 0 || burstSize <= 0) {
            throw new IllegalArgumentException("invalid weapon cycle values for " + id);
        }
        if (fireModes.stream().distinct().count() != fireModes.size()) {
            throw new IllegalArgumentException("duplicate fire mode for " + id);
        }
        if (mechanism == WeaponMechanism.PROJECTILE && ballistics.projectileSpeed() <= 0) {
            throw new IllegalArgumentException("projectile speed must be positive for " + id);
        }
        for (WeaponSoundCue cue : audio.reloadCues()) {
            if (cue.tick() > reloadTicks || audio.sound(cue.sound()) == null) {
                throw new IllegalArgumentException("invalid reload sound cue for " + id);
            }
        }
    }
}
