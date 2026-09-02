package com.theo.wizardpedia.fabric;

import com.theo.wizardpedia.Wizardpedia;
import com.theo.wizardpedia.server.WizardpediaServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;

public final class WizardpediaFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Wizardpedia.init();

        // Datapack catalog loading: scan + parse + push on /reload (SERVER_DATA).
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public ResourceLocation getFabricId() {
                        return Wizardpedia.id("catalog_data");
                    }

                    @Override
                    public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) {
                        WizardpediaServer.INSTANCE.load(manager);
                    }
                });
    }
}
