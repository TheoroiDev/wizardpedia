package com.theo.wizardpedia.item;

import net.minecraft.world.item.Item;
import java.util.function.Supplier;

/**
 * Registry slots filled by the platform modules (Fabric plain registry /
 * Forge DeferredRegister) — the common code only reads.
 */
public final class WizardpediaItems {

    public static Supplier<Item> BOOK = () -> null;

    private WizardpediaItems() {}
}
