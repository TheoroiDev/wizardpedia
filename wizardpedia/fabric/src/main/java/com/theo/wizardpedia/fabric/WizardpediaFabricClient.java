package com.theo.wizardpedia.fabric;

import com.theo.wizardpedia.Wizardpedia;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client wiring for Wizardpedia on Fabric. S2C catalog receiver registration
 * and screen hooks land here (M2/M3).
 */
public final class WizardpediaFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Wizardpedia.LOGGER.info("Wizardpedia client init (Fabric)");
    }
}
