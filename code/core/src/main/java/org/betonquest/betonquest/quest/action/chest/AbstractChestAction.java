package org.betonquest.betonquest.quest.action.chest;

import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.profile.Profile;
import org.betonquest.betonquest.api.quest.action.NullableAction;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.Nullable;

/**
 * Abstract class for chest actions.
 */
public abstract class AbstractChestAction implements NullableAction {

    /**
     * The location of the chest.
     */
    private final Argument<Location> location;

    /**
     * Creates a new chest clear action.
     *
     * @param location the location of the chest
     */
    public AbstractChestAction(final Argument<Location> location) {
        this.location = location;
    }

    /**
     * Resolves the location of the chest.
     *
     * @param profile the profile to resolve the location for
     * @return the location
     * @throws QuestException if the location cannot be resolved
     */
    protected Location getLocation(@Nullable final Profile profile) throws QuestException {
        return location.getValue(profile);
    }

    /**
     * Returns the chest at the specified location.
     *
     * @param resolvedLocation the already resolved location of the chest
     * @return the chest {@link InventoryHolder} at the specified location
     * @throws QuestException if there is no chest at the specified location
     */
    protected InventoryHolder getChest(final Location resolvedLocation) throws QuestException {
        final Block block = resolvedLocation.getBlock();
        try {
            return (InventoryHolder) block.getState(false);
        } catch (final ClassCastException e) {
            throw new QuestException("No chest found at location: X"
                    + block.getX() + " Y" + block.getY() + " Z" + block.getZ(), e);
        }
    }
}
