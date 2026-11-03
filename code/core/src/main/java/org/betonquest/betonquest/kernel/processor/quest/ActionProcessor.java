package org.betonquest.betonquest.kernel.processor.quest;

import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.identifier.ActionIdentifier;
import org.betonquest.betonquest.api.identifier.IdentifierFactory;
import org.betonquest.betonquest.api.logger.BetonQuestLogger;
import org.betonquest.betonquest.api.profile.Profile;
import org.betonquest.betonquest.api.quest.action.NullableActionAdapter;
import org.betonquest.betonquest.api.service.action.ActionManager;
import org.betonquest.betonquest.api.service.condition.ConditionManager;
import org.betonquest.betonquest.api.service.instruction.Instructions;
import org.betonquest.betonquest.id.IdentifierUtil;
import org.betonquest.betonquest.kernel.processor.TypedQuestProcessor;
import org.betonquest.betonquest.kernel.processor.adapter.ActionAdapter;
import org.betonquest.betonquest.kernel.registry.quest.ActionTypeRegistry;
import org.betonquest.betonquest.thread.ProfileThreads;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * Stores Actions and execute them.
 */
public class ActionProcessor extends TypedQuestProcessor<ActionIdentifier, ActionAdapter> implements ActionManager {

    /**
     * The plugin instance.
     */
    private final Plugin plugin;

    /**
     * Factory to create section actions for configuration sections.
     */
    private final SectionFactory<ActionIdentifier, ActionAdapter> sectionFactory;

    /**
     * Create a new Action Processor to store actions and execute them.
     *
     * @param log                     the custom logger for this class
     * @param actionIdentifierFactory the factory to create action identifiers
     * @param actionTypes             the available action types
     * @param instructionApi          the instruction api
     * @param plugin                  the plugin instance
     * @param conditionManager        the condition manager instance
     */
    public ActionProcessor(final BetonQuestLogger log,
                           final IdentifierFactory<ActionIdentifier> actionIdentifierFactory,
                           final ActionTypeRegistry actionTypes,
                           final Instructions instructionApi, final Plugin plugin, final ConditionManager conditionManager) {
        super(log, actionTypes, actionIdentifierFactory, instructionApi, "Action", "actions");
        this.plugin = plugin;
        this.sectionFactory = identifier -> {
            final List<ActionIdentifier> identifiers = IdentifierUtil.subsectionIdentifiers(identifierFactory, identifier);
            final NullableActionAdapter adapter = new NullableActionAdapter(profile -> this.run(profile, identifiers));
            return new ActionAdapter(log, conditionManager, identifier, adapter, adapter, profile -> List.of());
        };
    }

    @Override
    @Nullable
    protected SectionFactory<ActionIdentifier, ActionAdapter> getSectionFactory() {
        return sectionFactory;
    }

    @Override
    public boolean run(@Nullable final Profile profile, final Collection<ActionIdentifier> actionIDS) {
        if (ProfileThreads.isOwnedByCurrentThread(profile)) {
            return actionIDS.stream().map(actionID -> run(profile, actionID)).reduce(true, Boolean::logicalAnd);
        }

        final List<ActionIdentifier> syncList = new ArrayList<>();
        final List<ActionIdentifier> asyncList = new ArrayList<>();
        actionIDS.forEach(id -> {
            final ActionAdapter adapter = values.get(id);
            final boolean syncAsync = adapter != null && adapter.isPrimaryThreadEnforced();
            (syncAsync ? syncList : asyncList).add(id);
        });

        final Future<Boolean> syncFuture = syncList.isEmpty() ? CompletableFuture.completedFuture(true)
                : onOwningThread(profile, () -> syncList.stream().map(actionID -> run(profile, actionID))
                .reduce(true, Boolean::logicalAnd));
        final boolean asyncResult = asyncList.stream().map(actionID -> run(profile, actionID)).reduce(true, Boolean::logicalAnd);

        try {
            return asyncResult && syncFuture.get();
        } catch (final InterruptedException | ExecutionException e) {
            log.reportException(e);
            return true;
        }
    }

    @Override
    public boolean run(@Nullable final Profile profile, final ActionIdentifier actionID) {
        final ActionAdapter action = values.get(actionID);
        if (action == null) {
            log.warn(actionID.getPackage(), "Action " + actionID + " is not defined");
            return false;
        }
        if (profile == null) {
            log.debug(actionID.getPackage(), "Firing action " + actionID + " player independent");
        } else {
            log.debug(actionID.getPackage(), "Firing action " + actionID + " for " + profile);
        }
        if (action.isPrimaryThreadEnforced() && !ProfileThreads.isOwnedByCurrentThread(profile)) {
            return callActionSync(profile, actionID, action);
        }
        return callAction(profile, actionID, action);
    }

    private boolean callActionSync(@Nullable final Profile profile, final ActionIdentifier actionID, final ActionAdapter action) {
        try {
            return onOwningThread(profile, () -> callAction(profile, actionID, action)).get();
        } catch (final InterruptedException | ExecutionException e) {
            log.reportException(e);
            return true;
        }
    }

    /**
     * Runs the task on the server thread owning the profile's player.
     * <p>
     * An async thread waits for the result. A server thread that does not own the player (another region on Folia,
     * never the case on Paper) must not wait for another region, so the task is queued there and counted as
     * successful.
     *
     * @param profile the profile the actions run for
     * @param task    the task running the actions
     * @return the future result
     */
    private CompletableFuture<Boolean> onOwningThread(@Nullable final Profile profile, final Supplier<Boolean> task) {
        if (ProfileThreads.isServerThread()) {
            ProfileThreads.execute(plugin, profile, () -> {
                if (!task.get()) {
                    log.debug("Actions queued on the thread owning " + profile + " did not all succeed");
                }
            });
            return CompletableFuture.completedFuture(true);
        }
        return ProfileThreads.supply(plugin, profile, task);
    }

    private boolean callAction(@Nullable final Profile profile, final ActionIdentifier actionID, final ActionAdapter action) {
        try {
            return action.fire(profile);
        } catch (final QuestException e) {
            log.warn(actionID.getPackage(), "Error while firing '" + actionID + "' action: " + e.getMessage(), e);
            return true;
        }
    }
}
