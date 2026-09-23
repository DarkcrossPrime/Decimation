package com.decimation.client;

import com.decimation.client.content.ClientContentManager;
import com.decimation.client.firstperson.FirstPersonBodyRenderer;
import com.decimation.client.gun.ClientWeaponController;
import com.decimation.client.gun.WeaponObjRenderer;
import com.decimation.module.gun.GunModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;

public final class DecimationClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
            .registerReloadListener(ClientContentManager.INSTANCE);
        FirstPersonBodyRenderer.INSTANCE.register();
        ClientWeaponController.INSTANCE.register();
        GunModule.weapons().values().forEach(WeaponObjRenderer::register);
    }
}
