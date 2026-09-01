package com.theo.wizardpedia;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wizardpedia - a standalone, zero-dependency in-game catalog/compendium.
 *
 * <p>HOMM-style paginated book UI over a merged catalog fed by two sources:
 * server datapack entries ({@code data/<ns>/wizardpedia/...}) and the public
 * S2C {@code wizardpedia:catalog} wire channel (any provider mod can push,
 * zero compile-time dependency; wizardreal is the first consumer).
 *
 * <p>The loader modules bootstrap platform bindings (registries, networking,
 * events) and call {@link #init()}.
 */
public final class Wizardpedia {
    public static final String MOD_ID = "wizardpedia";
    public static final Logger LOGGER = LoggerFactory.getLogger("Wizardpedia");

    private static boolean initialized;

    private Wizardpedia() {}

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        LOGGER.info("Wizardpedia initializing");
    }
}
