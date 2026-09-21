package com.decimation.module.hud;

import com.decimation.Decimation;
import com.decimation.module.DecimationModule;
import net.minecraft.util.Identifier;

public final class HudModule implements DecimationModule {
    private static final Identifier ID = new Identifier(Decimation.MOD_ID, "hud");
    public Identifier id() { return ID; }
    public void initialize() { Decimation.LOGGER.debug("Initialized {} module", ID); }
}

