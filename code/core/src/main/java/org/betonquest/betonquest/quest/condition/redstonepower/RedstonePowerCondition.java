package org.betonquest.betonquest.quest.condition.redstonepower;

import org.betonquest.betonquest.BetonQuest;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.profile.Profile;
import org.betonquest.betonquest.api.quest.condition.NullableCondition;
import org.betonquest.betonquest.thread.Scheduling;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

/**
 * Condition to check if a block is powered.
 */
public class RedstonePowerCondition implements NullableCondition {

    /**
     * The location of the block to check for redstone power.
     */
    private final Argument<Location> location;

    /**
     * Whether the block is powered and how strongly.
     */
    private final Argument<Number> powered;

    /**
     * Creates a new redstone power condition.
     *
     * @param location the location of the block
     * @param powered  whether the block is powered
     */
    public RedstonePowerCondition(final Argument<Location> location, final Argument<Number> powered) {
        this.location = location;
        this.powered = powered;
    }

    @Override
    public boolean check(@Nullable final Profile profile) throws QuestException {
        final Location locationValue = location.getValue(profile);
        final int requiredPower = powered.getValue(profile).intValue();
        final int blockPower = Scheduling.callAtLocation(BetonQuest.getInstance(), locationValue,
                () -> locationValue.getBlock().getBlockPower());
        return blockPower >= requiredPower;
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
