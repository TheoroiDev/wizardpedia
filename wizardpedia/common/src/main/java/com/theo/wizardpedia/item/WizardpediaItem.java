package com.theo.wizardpedia.item;

import com.theo.wizardpedia.client.WizardpediaClientHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Wizardpedia book. Right-click opens the paginated catalog screen
 * (client side only; the server just acknowledges like vanilla written
 * books do with {@code sidedSuccess}).
 */
public class WizardpediaItem extends Item {

    public WizardpediaItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (world.isClientSide) {
            WizardpediaClientHooks.openScreen();
        }
        return InteractionResultHolder.sidedSuccess(stack, world.isClientSide());
    }
}
