package com.decimation;

import net.fabricmc.api.ModInitializer;
import com.decimation.module.gun.GunModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Decimation implements ModInitializer {
    public static final String MOD_ID = "decimation";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        GunModule.initialize();
        LOGGER.info("Decimation initialized: {} weapons, {} ammunition types", GunModule.weapons().size(), GunModule.ammunition().size());
    }
}
