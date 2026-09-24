package com.decimation.module.gun.data;

import java.util.Objects;

/** Definition-owned transforms for the supported weapon render contexts. */
public record WeaponPresentation(WeaponTransform firstPersonHip,
                                 WeaponTransform firstPersonAds,
                                 WeaponArmPose firstPersonHipArms,
                                 WeaponArmPose firstPersonAdsArms,
                                 WeaponTransform thirdPerson) {
    public WeaponPresentation {
        Objects.requireNonNull(firstPersonHip, "firstPersonHip");
        Objects.requireNonNull(firstPersonAds, "firstPersonAds");
        Objects.requireNonNull(firstPersonHipArms, "firstPersonHipArms");
        Objects.requireNonNull(firstPersonAdsArms, "firstPersonAdsArms");
        Objects.requireNonNull(thirdPerson, "thirdPerson");
    }
}
