package com.theo.wizardpedia.fabric;

import com.theo.wizardpedia.Wizardpedia;
import net.fabricmc.api.ModInitializer;

public final class WizardpediaFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Wizardpedia.init();
    }
}
