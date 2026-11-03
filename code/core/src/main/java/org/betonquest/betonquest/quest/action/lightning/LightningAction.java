package org.betonquest.betonquest.quest.action.lightning;

import org.betonquest.betonquest.BetonQuest;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.instruction.FlagArgument;
import org.betonquest.betonquest.api.profile.Profile;
import org.betonquest.betonquest.api.quest.action.NullableAction;
import org.betonquest.betonquest.thread.Scheduling;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

/**
 * Strikes a lightning at specified location.
 */
public class LightningAction implements NullableAction {

    /**
     * The location to strike the lightning at.
     */
    private final Argument<Location> location;

    /**
     * Whether the lightning should do damage.
     */
    private final FlagArgument<Boolean> noDamage;

    /**
     * Creates a new lightning action.
     *
     * @param location the location to strike the lightning at
     * @param noDamage whether the lightning should do damage
     */
    public LightningAction(final Argument<Location> location, final FlagArgument<Boolean> noDamage) {
        this.location = location;
        this.noDamage = noDamage;
    }

    @Override
    public void execute(@Nullable final Profile profile) throws QuestException {
        final Location resolvedLocation = location.getValue(profile);
        Scheduling.runQuestAtLocation(BetonQuest.getInstance(), resolvedLocation, () -> executeInRegion(profile, resolvedLocation));
    }

    private void executeInRegion(@Nullable final Profile profile, final Location resolvedLocation) throws QuestException {
        final World world = resolvedLocation.getWorld();
        if (noDamage.getValue(profile).orElse(false)) {
            world.strikeLightningEffect(resolvedLocation);
        } else {
            world.strikeLightning(resolvedLocation);
        }
    }

    @Override
    public boolean isPrimaryThreadEnforced() {
        return true;
    }
}
