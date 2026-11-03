package org.betonquest.betonquest.quest.condition.chest;

import org.betonquest.betonquest.BetonQuest;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.instruction.type.ItemWrapper;
import org.betonquest.betonquest.api.profile.Profile;
import org.betonquest.betonquest.api.quest.condition.NullableCondition;
import org.betonquest.betonquest.thread.Scheduling;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Checks if the chest contains specified items.
 */
public class ChestItemCondition implements NullableCondition {

    /**
     * Items that should be in the chest.
     */
    private final Argument<List<ItemWrapper>> items;

    /**
     * Location of the chest.
     */
    private final Argument<Location> loc;

    /**
     * Constructor of the ChestItemCondition.
     *
     * @param items items that should be in the chest
     * @param loc   location of the chest
     */
    public ChestItemCondition(final Argument<Location> loc, final Argument<List<ItemWrapper>> items) {
        this.items = items;
        this.loc = loc;
    }

    @Override
    public boolean check(@Nullable final Profile profile) throws QuestException {
        final Location location = loc.getValue(profile);
        final ItemStack[] inventoryItems = Scheduling.callAtLocation(BetonQuest.getInstance(), location, () -> copyContents(location.getBlock()));
        int counter = 0;
        final List<ItemWrapper> resolvedItems = items.getValue(profile);
        for (final ItemWrapper item : resolvedItems) {
            int amount = item.getAmount().getValue(profile).intValue();
            for (final ItemStack stack : inventoryItems) {
                if (stack == null) {
                    continue;
                }
                if (!item.matches(stack, profile)) {
                    continue;
                }
                amount -= stack.getAmount();
                if (amount <= 0) {
                    counter++;
                    break;
                }
            }
        }
        return counter == resolvedItems.size();
    }

    private ItemStack[] copyContents(final Block block) throws QuestException {
        final InventoryHolder chest;
        try {
            chest = (InventoryHolder) block.getState(false);
        } catch (final ClassCastException e) {
            throw new QuestException("Trying to check items in a chest, but there's no chest! Location: X" + block.getX() + " Y"
                    + block.getY() + " Z" + block.getZ(), e);
        }
        return Arrays.stream(chest.getInventory().getContents())
                .map(stack -> stack == null ? null : stack.clone())
                .toArray(ItemStack[]::new);
    }

    /**
     * Reads the world on its own: on the thread owning the location, waiting for it from an async thread.
     *
     * @return false, the condition does not need to start on the thread owning the profile
     */
    @Override
    public boolean isPrimaryThreadEnforced() {
        return false;
    }
}
