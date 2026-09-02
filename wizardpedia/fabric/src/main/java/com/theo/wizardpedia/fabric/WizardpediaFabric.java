package com.theo.wizardpedia.fabric;

import com.theo.wizardpedia.Wizardpedia;
import com.theo.wizardpedia.item.WizardpediaItem;
import com.theo.wizardpedia.item.WizardpediaItems;
import com.theo.wizardpedia.server.WizardpediaServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.function.Supplier;

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

        WizardpediaItems.BOOK = register("wizardpedia", new WizardpediaItem(new Item.Properties().stacksTo(1)));

        // Dedicated creative tab (icon = the book itself).
        CreativeModeTab tab = FabricItemGroup.builder()
                .icon(() -> new ItemStack(WizardpediaItems.BOOK.get()))
                .title(Component.translatable("itemGroup.wizardpedia.main"))
                .displayItems((displayContext, entries) -> entries.accept(WizardpediaItems.BOOK.get()))
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Wizardpedia.id("main"), tab);
    }

    private static Supplier<Item> register(String id, Item item) {
        Registry.register(BuiltInRegistries.ITEM, Wizardpedia.id(id), item);
        return () -> item;
    }
}
