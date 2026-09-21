package com.decimation.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModuleRegistry {
    private static final Map<String, DecimationModule> MODULES = new LinkedHashMap<>();

    private ModuleRegistry() {
    }

    public static void register(DecimationModule module) {
        String id = module.id().toString();
        if (MODULES.putIfAbsent(id, module) != null) {
            throw new IllegalStateException("Duplicate Decimation module: " + id);
        }
    }

    public static void initializeAll() {
        MODULES.values().forEach(DecimationModule::initialize);
    }

    public static List<DecimationModule> modules() {
        return Collections.unmodifiableList(new ArrayList<>(MODULES.values()));
    }
}

