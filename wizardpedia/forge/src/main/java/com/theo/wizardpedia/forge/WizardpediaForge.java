package com.theo.wizardpedia.forge;

import com.theo.wizardpedia.Wizardpedia;
import net.minecraftforge.fml.common.Mod;

@Mod(Wizardpedia.MOD_ID)
public final class WizardpediaForge {

    public WizardpediaForge() {
        Wizardpedia.init();
    }
}
