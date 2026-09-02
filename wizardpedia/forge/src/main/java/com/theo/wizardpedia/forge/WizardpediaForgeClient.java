package com.theo.wizardpedia.forge;

import com.theo.wizardpedia.Wizardpedia;
import com.theo.wizardpedia.client.WizardpediaClientHooks;
import com.theo.wizardpedia.client.WizardpediaScreen;
import com.theo.wizardpedia.net.CatalogNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Client initialization for Wizardpedia on Forge: registers the S2C catalog
 * receiver and installs the book-screen opener.
 *
 * <p>{@link FMLClientSetupEvent} is a MOD-bus event — this class MUST stay on
 * {@code Bus.MOD}. With the default FORGE bus the handler silently never
 * fires, which left wizardreal's chant HUD without its S2C receivers on Forge
 * (the Fabric side was unaffected) — the ChantNetwork lesson.
 */
@Mod.EventBusSubscriber(modid = Wizardpedia.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WizardpediaForgeClient {

    private WizardpediaForgeClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            CatalogNetwork.registerClientReceiver();
            WizardpediaClientHooks.setScreenOpener(WizardpediaScreen::new);
        });
    }
}
