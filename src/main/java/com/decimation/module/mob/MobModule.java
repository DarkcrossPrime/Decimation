package com.decimation.module.mob;

import com.decimation.Decimation;
import com.decimation.module.DecimationModule;
import net.minecraft.util.Identifier;

public final class MobModule implements DecimationModule {
    private static final Identifier ID = new Identifier(Decimation.MOD_ID, "mobs");
    public Identifier id() { return ID; }
    public void initialize() { Decimation.LOGGER.debug("Initialized {} module", ID); }
}

