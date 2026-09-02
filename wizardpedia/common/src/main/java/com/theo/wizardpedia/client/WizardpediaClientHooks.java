package com.theo.wizardpedia.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.util.function.Supplier;

/**
 * Loader-agnostic hook from common code into the client UI: the platform
 * client inits (Fabric entrypoint / Forge Bus.MOD setup) install a screen
 * supplier; {@link com.theo.wizardpedia.item.WizardpediaItem#use} calls
 * {@link #openScreen()} on the client side.
 *
 * <p>Client-only classes are referenced from method bodies only — safe under
 * lazy resolution since the server never invokes them.
 */
public final class WizardpediaClientHooks {

    private static volatile Supplier<Screen> screenOpener;

    private WizardpediaClientHooks() {}

    /** Install the platform screen opener (client init only). */
    public static void setScreenOpener(Supplier<Screen> opener) {
        screenOpener = opener;
    }

    /** Open the catalog screen (client side only). */
    public static void openScreen() {
        Supplier<Screen> opener = screenOpener;
        if (opener != null) {
            Minecraft.getInstance().setScreen(opener.get());
        }
    }
}
