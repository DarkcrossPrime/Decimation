package com.decimation.module.gun.data;

import java.util.List;
import net.minecraft.util.Identifier;

public record WeaponDefinition(Identifier id, String contentId, String displayName,
                               WeaponMechanism mechanism, AmmoDefinition ammo,
                               List<FireMode> fireModes, int burstSize, int rateOfFire,
                               int reloadTicks, BallisticsDefinition ballistics,
                               HandlingDefinition handling, WeaponAssets assets,
                               WeaponAudio audio) {
    public WeaponDefinition {
        fireModes = List.copyOf(fireModes);
        if (fireModes.isEmpty() || rateOfFire <= 0 || reloadTicks <= 0 || burstSize <= 0) {
            throw new IllegalArgumentException("invalid weapon cycle values for " + id);
        }
    }

    public FireMode firstFireMode() {
        return fireModes.get(0);
    }
}
