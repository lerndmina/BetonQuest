package org.betonquest.betonquest.quest.condition.eval;

import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.config.quest.QuestPackage;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.instruction.Instruction;
import org.betonquest.betonquest.api.profile.Profile;
import org.betonquest.betonquest.api.quest.TypeFactory;
import org.betonquest.betonquest.api.quest.condition.NullableCondition;
import org.betonquest.betonquest.api.quest.condition.PlayerCondition;
import org.betonquest.betonquest.api.quest.condition.PlayerlessCondition;
import org.betonquest.betonquest.api.service.instruction.Instructions;
import org.betonquest.betonquest.kernel.processor.adapter.ConditionAdapter;
import org.betonquest.betonquest.kernel.registry.QuestTypeRegistry;
import org.betonquest.betonquest.kernel.registry.quest.ConditionTypeRegistry;
import org.betonquest.betonquest.thread.ProfileThreads;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * A condition which evaluates to another condition.
 */
public class EvalCondition implements NullableCondition {

    /**
     * The BetonQuest instructions to use to parse instructions.
     */
    private final Instructions instructions;

    /**
     * The condition type registry providing factories to parse the evaluated instruction.
     */
    private final ConditionTypeRegistry conditionTypeRegistry;

    /**
     * The quest package to relate the condition to.
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
     * Creates a new Eval condition.
     *
     * @param instructions          the {@link Instructions} to use to parse instructions
     * @param conditionTypeRegistry the condition type registry providing factories to parse the evaluated instruction
     * @param pack                  the quest package to relate the condition to
     * @param evaluation            the evaluation input
     * @param plugin                the plugin instance
     */
    public EvalCondition(final Instructions instructions, final ConditionTypeRegistry conditionTypeRegistry,
                         final QuestPackage pack, final Argument<String> evaluation, final Plugin plugin) {
        this.instructions = instructions;
        this.conditionTypeRegistry = conditionTypeRegistry;
        this.pack = pack;
        this.evaluation = evaluation;
        this.plugin = plugin;
    }

    /**
     * Constructs a condition with a given instruction and returns it.
     *
     * @param instructions          the {@link Instructions} to use to parse instructions
     * @param instruction           the instruction string to parse
     * @param conditionTypeRegistry the condition type registry providing factories to parse the evaluated instruction
     * @param pack                  the quest package to relate the condition to
     * @return the condition
     * @throws QuestException if the condition could not be created
     */
    public static ConditionAdapter createCondition(final Instructions instructions, final QuestTypeRegistry<PlayerCondition, PlayerlessCondition,
            ConditionAdapter> conditionTypeRegistry, final QuestPackage pack, final String instruction) throws QuestException {
        final Instruction conditionInstruction = instructions.create(pack, instruction);
        final TypeFactory<ConditionAdapter> conditionFactory = conditionTypeRegistry.getFactory(conditionInstruction.getPart(0));
        return conditionFactory.parseInstruction(conditionInstruction);
    }

    @Override
    public boolean check(@Nullable final Profile profile) throws QuestException {
        final ConditionAdapter condition = createCondition(instructions, conditionTypeRegistry, pack, evaluation.getValue(profile));
        if (condition.isPrimaryThreadEnforced() && !ProfileThreads.isServerThread()) {
            return ProfileThreads.call(plugin, profile, () -> condition.check(profile));
        }
        return condition.check(profile);
    }
}
