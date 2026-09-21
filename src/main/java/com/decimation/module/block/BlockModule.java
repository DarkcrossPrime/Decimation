package com.decimation.module.block;

import com.decimation.Decimation;
import com.decimation.module.DecimationModule;
import net.minecraft.util.Identifier;

public final class BlockModule implements DecimationModule {
    private static final Identifier ID = new Identifier(Decimation.MOD_ID, "blocks");
    public Identifier id() { return ID; }
    public void initialize() { Decimation.LOGGER.debug("Initialized {} module", ID); }
}

