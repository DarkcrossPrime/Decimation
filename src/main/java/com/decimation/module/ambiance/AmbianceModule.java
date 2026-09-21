package com.decimation.module.ambiance;

import com.decimation.Decimation;
import com.decimation.module.DecimationModule;
import net.minecraft.util.Identifier;

public final class AmbianceModule implements DecimationModule {
    private static final Identifier ID = new Identifier(Decimation.MOD_ID, "ambiance");
    public Identifier id() { return ID; }
    public void initialize() { Decimation.LOGGER.debug("Initialized {} module", ID); }
}

