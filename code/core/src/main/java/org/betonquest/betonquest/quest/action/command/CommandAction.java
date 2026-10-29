package org.betonquest.betonquest.quest.action.command;

import org.betonquest.betonquest.BetonQuest;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.bukkit.command.SilentConsoleCommandSender;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.profile.Profile;
import org.betonquest.betonquest.api.quest.action.NullableAction;
import org.betonquest.betonquest.thread.Scheduling;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Action that runs given commands in the server console.
 */
public class CommandAction implements NullableAction {

    /**
     * Command sender to run the commands as.
     * <p>
     * {@link SilentConsoleCommandSender} is used to keep console and log clean.
     */
    private final CommandSender silentSender;

    /**
     * Server to run the commands on.
     */
    private final Server server;

    /**
     * The commands to run.
     */
    private final List<Argument<String>> commands;

    /**
     * Creates a new CommandAction.
     *
     * @param commands     the commands to run
     * @param silentSender the command sender to run the commands as
     * @param server       the server to run the commands on
     */
    public CommandAction(final List<Argument<String>> commands, final CommandSender silentSender, final Server server) {
        this.silentSender = silentSender;
        this.server = server;
        this.commands = commands;
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    @Override
    public void execute(@Nullable final Profile profile) throws QuestException {
        final List<String> resolved = new ArrayList<>(commands.size());
        for (final Argument<String> command : commands) {
            resolved.add(command.getValue(profile));
        }
        if (!Scheduling.isGlobalTickThread()) {
            // console commands must run on the global region thread, on Folia this is not the player's thread
            Scheduling.runGlobal(BetonQuest.getInstance(), () -> dispatchLogged(resolved));
            return;
        }
        try {
            resolved.forEach(command -> server.dispatchCommand(silentSender, command));
        } catch (final RuntimeException exception) {
            throw new QuestException("Unhandled exception executing command: " + exception.getMessage(), exception);
        }
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private void dispatchLogged(final List<String> resolved) {
        try {
            resolved.forEach(command -> server.dispatchCommand(silentSender, command));
        } catch (final RuntimeException exception) {
            server.getLogger().log(Level.WARNING, "Unhandled exception executing BetonQuest command action: " + exception.getMessage(), exception);
        }
    }

    @Override
    public boolean isPrimaryThreadEnforced() {
        return true;
    }
}
