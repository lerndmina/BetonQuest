package org.betonquest.betonquest.notify.io;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.config.quest.QuestPackage;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.profile.OnlineProfile;
import org.betonquest.betonquest.api.service.placeholder.PlaceholderManager;
import org.betonquest.betonquest.notify.NotifyIO;
import org.betonquest.betonquest.thread.Scheduling;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Displays the message as boss bar.
 */
public class BossBarNotifyIO extends NotifyIO {

    /**
     * Plugin to start tasks.
     */
    private final Plugin plugin;

    /**
     * The flags for the boss bar.
     */
    private final List<BossBar.Flag> flags;

    /**
     * The color of the boss bar.
     */
    private final BossBar.Color color;

    /**
     * The style of the boss bar.
     */
    private final BossBar.Overlay style;

    /**
     * The progress of the boss bar.
     */
    private final Argument<Number> progress;

    /**
     * The time the boss bar should stay visible.
     */
    private final Argument<Number> stay;

    /**
     * The countdown for the boss bar.
     */
    private final Argument<Number> countdown;

    /**
     * Create a new Boss Bar Notify IO.
     *
     * @param placeholders the {@link PlaceholderManager} to create and resolve placeholders
     * @param pack         the source pack to resolve placeholders
     * @param data         the customization data for notifications
     * @param plugin       the plugin to start tasks
     * @throws QuestException when data could not be parsed
     */
    @SuppressWarnings("PMD.CyclomaticComplexity")
    public BossBarNotifyIO(final PlaceholderManager placeholders, @Nullable final QuestPackage pack, final Map<String, String> data, final Plugin plugin) throws QuestException {
        super(placeholders, pack, data);
        this.plugin = plugin;

        flags = new ArrayList<>();
        if (data.containsKey("barflags")) {
            for (final String flag : data.get("barflags").split(",")) {
                final String upperCaseFlag = flag.toUpperCase(Locale.ROOT);
                try {
                    flags.add(BossBar.Flag.valueOf(upperCaseFlag));
                } catch (final IllegalArgumentException exception) {
                    throw new QuestException(String.format(CATCH_MESSAGE_TYPE, "BarFlag", upperCaseFlag), exception);
                }
            }
        }

        final String barColorString = data.get("barcolor");
        final String upperCaseBarColor = barColorString == null ? null : barColorString.toUpperCase(Locale.ROOT);
        try {
            color = upperCaseBarColor == null ? BossBar.Color.BLUE : BossBar.Color.valueOf(upperCaseBarColor);
        } catch (final IllegalArgumentException exception) {
            throw new QuestException(String.format(CATCH_MESSAGE_TYPE, "BarColor", upperCaseBarColor), exception);
        }

        final String styleString = data.get("style");
        final String upperCaseBarStyle = styleString == null ? null : styleString.toUpperCase(Locale.ROOT);
        try {
            style = styleString == null ? BossBar.Overlay.PROGRESS : BossBar.Overlay.valueOf(upperCaseBarStyle);
        } catch (final IllegalArgumentException exception) {
            throw new QuestException(String.format(CATCH_MESSAGE_TYPE, "BarStyle", upperCaseBarStyle), exception);
        }

        progress = getNumberData("progress", 1);
        stay = getNumberData("stay", 70);
        countdown = getNumberData("countdown", 0);
    }

    @Override
    protected void notifyPlayer(final Component message, final OnlineProfile onlineProfile) throws QuestException {
        final float progress = Math.max(0.0F, Math.min(1.0F, this.progress.getValue(onlineProfile).floatValue()));
        final int countdown = this.countdown.getValue(onlineProfile).intValue();
        final int stay = Math.max(0, this.stay.getValue(onlineProfile).intValue());

        final BossBar bossBar = BossBar.bossBar(message, progress, color, style);
        for (final BossBar.Flag flag : flags) {
            bossBar.addFlag(flag);
        }
        onlineProfile.getPlayer().showBossBar(bossBar);

        scheduleRemoval(bossBar, onlineProfile, stay);
        if (countdown > 0) {
            final int interval = stay / countdown;
            final float amount = progress / countdown;
            scheduleAnimation(bossBar, interval, amount, countdown, progress);
        }
    }

    private void scheduleRemoval(final BossBar bossBar, final OnlineProfile onlineProfile, final int stay) {
        final Player player = onlineProfile.getPlayer();
        Scheduling.runForEntityLater(plugin, player, () -> player.hideBossBar(bossBar), stay);
    }

    private void scheduleAnimation(final BossBar bossBar, final int interval, final float amount,
                                   final int startCountdown, final float startProgress) {
        final int[] currentCountdown = {startCountdown};
        final float[] currentProgress = {startProgress};
        Scheduling.runGlobalTimer(plugin, task -> {
            if (currentCountdown[0] == 0) {
                task.cancel();
                return;
            }
            currentCountdown[0] -= 1;
            currentProgress[0] -= amount;
            bossBar.progress(Math.max(0.0F, currentProgress[0]));
        }, interval, interval);
    }
}
