package com.theo.wizardpedia;

import com.theo.wizardpedia.server.WizardpediaServer;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
        // The server must be captured before datapack reload listeners fire
        // (datapack load runs between SERVER_STARTING and SERVER_STARTED).
        LifecycleEvent.SERVER_STARTING.register(WizardpediaServer::setServer);
        LifecycleEvent.SERVER_STARTED.register(WizardpediaServer::setServer);
        LifecycleEvent.SERVER_STOPPED.register(server -> WizardpediaServer.setServer(null));
        // Every joining player gets the full catalog (datapack source).
        // Architectury's PLAYER_JOIN callback already hands us a ServerPlayer.
        PlayerEvent.PLAYER_JOIN.register(WizardpediaServer.INSTANCE::sync);
    }
}
