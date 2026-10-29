package org.betonquest.betonquest.quest.action.eval;

import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.config.quest.QuestPackage;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.instruction.Instruction;
import org.betonquest.betonquest.api.profile.Profile;
import org.betonquest.betonquest.api.quest.TypeFactory;
import org.betonquest.betonquest.api.quest.action.NullableAction;
import org.betonquest.betonquest.api.service.instruction.Instructions;
import org.betonquest.betonquest.kernel.processor.adapter.ActionAdapter;
import org.betonquest.betonquest.kernel.registry.quest.ActionTypeRegistry;
import org.betonquest.betonquest.thread.ProfileThreads;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * An action which evaluates to another action.
 */
public class EvalAction implements NullableAction {

    /**
     * The BetonQuest instructions to use to parse instructions.
     */
    private final Instructions instructions;

    /**
     * The action type registry providing factories to parse the evaluated instruction.
     */
    private final ActionTypeRegistry actionTypeRegistry;

    /**
     * The quest package to relate the action to.
     */
    private final QuestPackage pack;

    /**
     * The evaluation input.
     */
    private final Argument<String> evaluation;

    /**
     * The plugin instance.
     */
    private final Plugin plugin;

    /**
     * Created a new Eval action.
     *
     * @param instructions       the BetonQuest instructions to use to parse instructions
     * @param actionTypeRegistry the action type registry providing factories to parse the evaluated instruction
     * @param pack               the quest package to relate the action to
     * @param evaluation         the evaluation input
     * @param plugin             the plugin instance
     */
    public EvalAction(final Instructions instructions, final ActionTypeRegistry actionTypeRegistry, final QuestPackage pack,
                      final Argument<String> evaluation, final Plugin plugin) {
        this.instructions = instructions;
        this.actionTypeRegistry = actionTypeRegistry;
        this.pack = pack;
        this.evaluation = evaluation;
        this.plugin = plugin;
    }

    /**
     * Constructs an action with a given instruction and returns it.
     *
     * @param instructions       the {@link Instructions} to use to parse instructions
     * @param instruction        the instruction string to parse
     * @param actionTypeRegistry the action type registry providing factories to parse the evaluated instruction
     * @param pack               the quest package to relate the action to
     * @return the action
     * @throws QuestException if the action could not be created
     */
    public static ActionAdapter createAction(final Instructions instructions, final ActionTypeRegistry actionTypeRegistry,
                                             final QuestPackage pack, final String instruction) throws QuestException {
        final Instruction actionInstruction = instructions.create(pack, instruction);
        final TypeFactory<ActionAdapter> actionFactory = actionTypeRegistry.getFactory(actionInstruction.getPart(0));
        return actionFactory.parseInstruction(actionInstruction);
    }

    @Override
    public void execute(@Nullable final Profile profile) throws QuestException {
        final ActionAdapter action = createAction(instructions, actionTypeRegistry, pack, evaluation.getValue(profile));
        if (!action.isPrimaryThreadEnforced() || ProfileThreads.isOwnedByCurrentThread(profile)) {
            action.fire(profile);
        } else if (ProfileThreads.isServerThread()) {
            ProfileThreads.execute(plugin, profile, () -> fireLogged(action, profile));
        } else {
            ProfileThreads.call(plugin, profile, () -> action.fire(profile));
        }
    }

    private void fireLogged(final ActionAdapter action, @Nullable final Profile profile) {
        try {
            action.fire(profile);
        } catch (final QuestException e) {
            plugin.getLogger().warning("Error while firing evaluated action in '" + pack.getQuestPath() + "': " + e.getMessage());
        }
    }
}
