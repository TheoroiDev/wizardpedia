package com.theo.wizardpedia.forge;

import com.theo.wizardpedia.Wizardpedia;
import com.theo.wizardpedia.server.WizardpediaServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;

/**
 * GAME-bus (FORGE) event handlers.
 *
 * <p>{@link AddReloadListenerEvent} is NOT an {@code IModBusEvent} — it fires
 * on the game bus; registering it on the MOD bus fails at CONSTRUCT with
 * "not a subtype of the base type interface IModBusEvent".
 */
@Mod.EventBusSubscriber(modid = Wizardpedia.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
final class WizardpediaForgeEvents {

    private WizardpediaForgeEvents() {}

    /** Datapack catalog loading: scan + parse + push on /reload. */
    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimplePreparableReloadListener<ResourceManager>() {
            @Override
            protected ResourceManager prepare(ResourceManager manager, ProfilerFiller profiler) {
                return manager;
            }

            @Override
            protected void apply(ResourceManager manager, ResourceManager resourceManager,
                                 ProfilerFiller profiler) {
                WizardpediaServer.INSTANCE.load(manager);
            }
        });
    }
}
