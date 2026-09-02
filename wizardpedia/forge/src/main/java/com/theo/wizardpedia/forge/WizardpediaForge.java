package com.theo.wizardpedia.forge;

import com.theo.wizardpedia.Wizardpedia;
import com.theo.wizardpedia.item.WizardpediaItem;
import com.theo.wizardpedia.item.WizardpediaItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(Wizardpedia.MOD_ID)
public final class WizardpediaForge {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Wizardpedia.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(), Wizardpedia.MOD_ID);

    public static final ResourceKey<CreativeModeTab> MAIN_TAB_KEY =
            ResourceKey.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(),
                    new ResourceLocation(Wizardpedia.MOD_ID, "main"));

    public WizardpediaForge() {
        var modEventBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modEventBus);
        TABS.register(modEventBus);

        RegistryObject<Item> book = ITEMS.register("wizardpedia",
                () -> new WizardpediaItem(new Item.Properties().stacksTo(1)));
        WizardpediaItems.BOOK = book;

        TABS.register("main", () -> CreativeModeTab.builder(CreativeModeTab.Row.BOTTOM, 0)
                .title(Component.translatable("itemGroup.wizardpedia.main"))
                .icon(() -> new ItemStack(WizardpediaItems.BOOK.get()))
                .displayItems((parameters, output) -> output.accept(WizardpediaItems.BOOK.get()))
                .build());

        Wizardpedia.init();
    }
}
