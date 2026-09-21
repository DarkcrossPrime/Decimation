package com.decimation;

import com.decimation.module.ModuleRegistry;
import com.decimation.module.ambiance.AmbianceModule;
import com.decimation.module.block.BlockModule;
import com.decimation.module.gun.GunModule;
import com.decimation.module.hud.HudModule;
import com.decimation.module.mob.MobModule;
import com.decimation.module.vehicle.VehicleModule;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Decimation implements ModInitializer {
    public static final String MOD_ID = "decimation";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModuleRegistry.register(new AmbianceModule());
        ModuleRegistry.register(new GunModule());
        ModuleRegistry.register(new VehicleModule());
        ModuleRegistry.register(new HudModule());
        ModuleRegistry.register(new BlockModule());
        ModuleRegistry.register(new MobModule());
        ModuleRegistry.initializeAll();
        LOGGER.info("Decimation initialized with {} modules", ModuleRegistry.modules().size());
    }
}

