package org.betonquest.betonquest.quest.objective.delay;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.betonquest.betonquest.BetonQuest;
import org.betonquest.betonquest.api.DefaultObjective;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.config.Localizations;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.instruction.FlagArgument;
import org.betonquest.betonquest.api.instruction.argument.parser.NumberParser;
import org.betonquest.betonquest.api.profile.Profile;
import org.betonquest.betonquest.api.quest.objective.service.ObjectiveProperties;
import org.betonquest.betonquest.api.quest.objective.service.ObjectiveService;
import org.betonquest.betonquest.thread.ProfileThreads;
import org.betonquest.betonquest.thread.Scheduling;
import org.betonquest.betonquest.thread.TaskHandle;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Player has to wait specified amount of time. He may logout, the objective
 * will be completed as soon as the time is up and he logs in again.
 */
public class DelayObjective extends DefaultObjective {

    /**
     * The delay time in seconds, minutes, or ticks.
     */
    private final Argument<Number> delay;

    /**
     * The flag for parsing the delay time as ticks.
     */
    private final FlagArgument<Boolean> ticks;

    /**
     * The flag for parsing the delay time as seconds.
     */
    private final FlagArgument<Boolean> seconds;

    /**
     * The runnable task that checks the delay.
     */
    private final TaskHandle runnable;

    /**
     * Constructor for the DelayObjective.
     *
     * @param service  the objective service
     * @param interval the interval in ticks at which the objective checks if the time is up
     * @param delay    the delay time in seconds, minutes, or ticks
     * @param ticks    the flag for parsing the delay time as ticks
     * @param seconds  the flag for parsing the delay time as seconds
     * @throws QuestException if there is an error in the instruction
     */
    public DelayObjective(final ObjectiveService service, final Argument<Number> interval,
                          final Argument<Number> delay, final FlagArgument<Boolean> ticks, final FlagArgument<Boolean> seconds) throws QuestException {
        super(service);
        this.delay = delay;
        this.ticks = ticks;
        this.seconds = seconds;
        this.runnable = Scheduling.runGlobalTimer(BetonQuest.getInstance(), task -> {
            final long time = System.currentTimeMillis();
            // iterate a copy, completing the objective changes the data
            for (final Profile profile : List.copyOf(service.getData().keySet())) {
                ProfileThreads.execute(BetonQuest.getInstance(), profile, () -> {
                    final boolean profileConditions = getExceptionHandler().handle(() -> service.checkConditions(profile), false);
                    if (profileConditions && time >= getTargetTimestamp(profile)) {
                        service.complete(profile);
                    }
                });
            }
        }, 1, interval.getValue(null).longValue());
        service.setDefaultData(this::getDefaultDataInstruction);
        final ObjectiveProperties properties = service.getProperties();
        properties.setProperty("left", profile ->
                getExceptionHandler().handle(() -> LegacyComponentSerializer.legacySection().serialize(parseLeftProperty(profile)), ""));
        properties.setProperty("date", this::parseDateProperty);
        properties.setProperty("rawseconds", this::parseRawSecondsProperty);
    }

    private long timeToMilliSeconds(final Profile profile, final long time) throws QuestException {
        if (ticks.getValue(profile).orElse(false)) {
            return time * 50;
        }
        if (seconds.getValue(profile).orElse(false)) {
            return time * 1000;
        }
        return time * 1000 * 60;
    }

    @Override
    public void close() {
        runnable.cancel();
        super.close();
    }

    private String getDefaultDataInstruction(final Profile profile) throws QuestException {
        final long millis = timeToMilliSeconds(profile, delay.getValue(profile).longValue());
        return Long.toString(System.currentTimeMillis() + millis);
    }

    private Component parseLeftProperty(final Profile profile) throws QuestException {
        final Localizations localizations = BetonQuest.getInstance().getBetonQuestApi().localizations();
        final Component daysWord = localizations.getMessage(profile, "days");
        final Component daysWordSingular = localizations.getMessage(profile, "days_singular");
        final Component hoursWord = localizations.getMessage(profile, "hours");
        final Component hoursWordSingular = localizations.getMessage(profile, "hours_singular");
        final Component minutesWord = localizations.getMessage(profile, "minutes");
        final Component minutesWordSingular = localizations.getMessage(profile, "minutes_singular");
        final Component secondsWord = localizations.getMessage(profile, "seconds");
        final Component secondsWordSingular = localizations.getMessage(profile, "seconds_singular");

        final long endTimestamp = getTargetTimestamp(profile);
        final LocalDateTime end = LocalDateTime.ofInstant(Instant.ofEpochMilli(endTimestamp), ZoneId.systemDefault());
        final Duration duration = Duration.between(LocalDateTime.now(), end);

        final TextComponent.Builder builder = Component.text();
        buildTimeDescription(builder, daysWord, daysWordSingular, duration.toDaysPart());
        buildTimeDescription(builder, hoursWord, hoursWordSingular, duration.toHoursPart());
        buildTimeDescription(builder, minutesWord, minutesWordSingular, duration.toMinutesPart());
        buildTimeDescription(builder, secondsWord, secondsWordSingular, duration.toSecondsPart());

        return builder.build();
    }

    @SuppressWarnings("PMD.AvoidLiteralsInIfCondition")
    private void buildTimeDescription(final TextComponent.Builder builder, final Component timeUnitWord, final Component timeUnitSingularWord, final long timeAmount) {
        if (!builder.children().isEmpty()) {
            builder.append(Component.space());
        }
        if (timeAmount > 1) {
            builder.append(Component.text(timeAmount)).append(Component.space()).append(timeUnitWord);
        } else if (timeAmount == 1) {
            builder.append(Component.text(timeAmount)).append(Component.space()).append(timeUnitSingularWord);
        }
    }

    private String parseDateProperty(final Profile profile) {
        final String pattern = BetonQuest.getInstance().getPluginConfig().getString("date_format", "");
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern, Locale.ROOT);
        final long millis = getTargetTimestamp(profile);
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(formatter);
    }

    private String parseRawSecondsProperty(final Profile profile) {
        final long timeLeft = getTargetTimestamp(profile) - System.currentTimeMillis();
        return String.valueOf(timeLeft / 1000);
    }

    private long getTargetTimestamp(final Profile profile) {
        final String data = getService().getData().get(profile);
        if (data == null) {
            return System.currentTimeMillis();
        }
        try {
            return NumberParser.DEFAULT.apply(data).longValue();
        } catch (final QuestException e) {
            return System.currentTimeMillis();
        }
    }
}
