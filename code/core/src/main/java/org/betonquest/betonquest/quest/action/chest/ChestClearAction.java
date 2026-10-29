package org.betonquest.betonquest.quest.action.chest;

import org.betonquest.betonquest.BetonQuest;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.profile.Profile;
import org.betonquest.betonquest.thread.Scheduling;
import org.bukkit.Location;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.Nullable;

/**
 * Clears a specified chest from all items inside.
 */
public class ChestClearAction extends AbstractChestAction {

    /**
     * Creates a new chest clear action.
     *
     * @param location the location of the chest
     */
    public ChestClearAction(final Argument<Location> location) {
        super(location);
    }

    @Override
    public void execute(@Nullable final Profile profile) throws QuestException {
        Scheduling.runQuestAtLocation(BetonQuest.getInstance(), getLocation(profile), () -> executeInRegion(profile));
    }

    private void executeInRegion(@Nullable final Profile profile) throws QuestException {
        try {
            final InventoryHolder chest = getChest(profile);
            chest.getInventory().clear();
        } catch (final QuestException e) {
            throw new QuestException("Trying to clear chest. " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isPrimaryThreadEnforced() {
        return true;
    }
}
