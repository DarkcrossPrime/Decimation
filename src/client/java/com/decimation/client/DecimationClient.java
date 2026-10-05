package com.decimation.client;

import com.decimation.Decimation;
import com.decimation.client.gun.ClientWeaponController;
import com.decimation.client.gun.ClientWeaponAudio;
import com.decimation.client.gun.WeaponModelLoading;
import com.decimation.client.gun.WeaponAdsPresentation;
import com.decimation.client.firstperson.FirstPersonBodyRenderer;
import net.fabricmc.api.ClientModInitializer;

public final class DecimationClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        new ClientWeaponController().register();
        ClientWeaponAudio.register();
        WeaponModelLoading.register();
        FirstPersonBodyRenderer.register();
        WeaponAdsPresentation.register();
        Decimation.LOGGER.info("Decimation client initialized; weapon input, audio, OBJ visuals and captured body/arm rig ready");
    }
}
