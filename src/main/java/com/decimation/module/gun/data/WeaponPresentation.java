package com.decimation.module.gun.data;

import java.util.Objects;

/** Definition-owned transforms for the supported weapon render contexts. */
public record WeaponPresentation(WeaponTransform firstPersonHip,
                                 WeaponTransform firstPersonAds,
                                 WeaponTransform firstPersonSprint,
                                 WeaponArmPose firstPersonHipArms,
                                 WeaponArmPose firstPersonAdsArms,
                                 WeaponArmPose firstPersonSprintArms,
                                 WeaponTransform thirdPerson) {
    public WeaponPresentation {
        Objects.requireNonNull(firstPersonHip, "firstPersonHip");
        Objects.requireNonNull(firstPersonAds, "firstPersonAds");
        Objects.requireNonNull(firstPersonSprint, "firstPersonSprint");
        Objects.requireNonNull(firstPersonHipArms, "firstPersonHipArms");
        Objects.requireNonNull(firstPersonAdsArms, "firstPersonAdsArms");
        Objects.requireNonNull(firstPersonSprintArms, "firstPersonSprintArms");
        Objects.requireNonNull(thirdPerson, "thirdPerson");
    }
}
