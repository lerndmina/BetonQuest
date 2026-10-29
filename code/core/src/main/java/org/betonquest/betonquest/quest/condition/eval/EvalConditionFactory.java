package org.betonquest.betonquest.quest.condition.eval;

import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.instruction.Instruction;
import org.betonquest.betonquest.api.quest.condition.NullableConditionAdapter;
import org.betonquest.betonquest.api.quest.condition.PlayerCondition;
import org.betonquest.betonquest.api.quest.condition.PlayerConditionFactory;
import org.betonquest.betonquest.api.quest.condition.PlayerlessCondition;
import org.betonquest.betonquest.api.quest.condition.PlayerlessConditionFactory;
import org.betonquest.betonquest.api.service.instruction.Instructions;
import org.betonquest.betonquest.kernel.registry.quest.ConditionTypeRegistry;
import org.bukkit.plugin.Plugin;

/**
 * A factory for creating Eval conditions.
 */
public class EvalConditionFactory implements PlayerConditionFactory, PlayerlessConditionFactory {

    /**
     * The BetonQuest instructions used to parse instructions.
     */
    private final Instructions instructions;

    /**
     * The condition type registry providing factories to parse the evaluated instruction.
     */
    private final ConditionTypeRegistry conditionTypeRegistry;

    /**
     * The plugin instance.
     */
    private final Plugin plugin;

    /**
     * Creates a new Eval condition factory.
     *
     * @param instructions          the BetonQuest instructions used to parse instructions
     * @param conditionTypeRegistry the condition type registry providing factories to parse the evaluated instruction
     * @param plugin                the plugin instance
     */
    public EvalConditionFactory(final Instructions instructions,
                                final ConditionTypeRegistry conditionTypeRegistry, final Plugin plugin) {
        this.instructions = instructions;
        this.conditionTypeRegistry = conditionTypeRegistry;
        this.plugin = plugin;
    }

    @Override
    public PlayerCondition parsePlayer(final Instruction instruction) throws QuestException {
        return parseEvalCondition(instruction);
    }

    @Override
    public PlayerlessCondition parsePlayerless(final Instruction instruction) throws QuestException {
        return parseEvalCondition(instruction);
    }

    private NullableConditionAdapter parseEvalCondition(final Instruction instruction) throws QuestException {
        final String rawInstruction = String.join(" ", instruction.getValueParts());
        return new NullableConditionAdapter(new EvalCondition(instructions, conditionTypeRegistry,
                instruction.getPackage(), instruction.chainForArgument(rawInstruction).string().get(), plugin));
    }
}
