package com.theo.wizardpedia.fabric;

import com.theo.wizardpedia.net.CatalogNetwork;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client wiring for Wizardpedia on Fabric: registers the S2C catalog
 * receiver and (M3) opens the book screen from the item hook.
 */
public final class WizardpediaFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CatalogNetwork.registerClientReceiver();
    }
}
