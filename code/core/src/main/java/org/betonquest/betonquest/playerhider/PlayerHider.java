package org.betonquest.betonquest.playerhider;

import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.config.ConfigAccessor;
import org.betonquest.betonquest.api.config.quest.QuestPackage;
import org.betonquest.betonquest.api.config.quest.QuestPackageManager;
import org.betonquest.betonquest.api.identifier.ConditionIdentifier;
import org.betonquest.betonquest.api.logger.BetonQuestLogger;
import org.betonquest.betonquest.api.profile.OnlineProfile;
import org.betonquest.betonquest.api.profile.Profile;
import org.betonquest.betonquest.api.profile.ProfileProvider;
import org.betonquest.betonquest.api.service.condition.ConditionManager;
import org.betonquest.betonquest.api.service.instruction.Instructions;
import org.betonquest.betonquest.thread.ProfileThreads;
import org.betonquest.betonquest.thread.Scheduling;
import org.betonquest.betonquest.thread.TaskHandle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The {@link PlayerHider} can hide others, if both the source and the target {@link Profile} meet all conditions.
 */
public class PlayerHider {

    /**
     * The map's key is an array containing the source {@link Profile}'s conditions
     * and the map's value is an array containing the target {@link Profile}'s conditions.
     */
    private final Map<Collection<ConditionIdentifier>, Collection<ConditionIdentifier>> hiders;

    /**
     * Plugin instance to show/hide players.
     */
    private final Plugin plugin;

    /**
     * The logger to use.
     */
    private final BetonQuestLogger log;

    /**
     * The condition manager instance.
     */
    private final ConditionManager conditionManager;

    /**
     * The profile provider instance.
     */
    private final ProfileProvider profileProvider;

    /**
     * The running hider.
     */
    @Nullable
    private TaskHandle bukkitTask;

    /**
     * Initialize and start a new {@link PlayerHider}.
     *
     * @param plugin           the plugin instance
     * @param log              the logger to use
     * @param conditionManager the condition manager instance
     * @param profileProvider  the profile provider instance
     */
    public PlayerHider(final Plugin plugin, final BetonQuestLogger log,
                       final ConditionManager conditionManager, final ProfileProvider profileProvider) {
        this.plugin = plugin;
        this.log = log;
        this.conditionManager = conditionManager;
        this.profileProvider = profileProvider;
        hiders = new HashMap<>();
    }

    /**
     * Loads the hiders from the quest packages.
     *
     * @param questPackageManager the quest package manager
     * @param instructions        the instructions instance
     */
    public void load(final QuestPackageManager questPackageManager, final Instructions instructions) {
        hiders.clear();
        try {
            for (final QuestPackage pack : questPackageManager.getPackages().values()) {
                final ConfigurationSection hiderSection = pack.getConfig().getConfigurationSection("player_hider");
                if (hiderSection == null) {
                    continue;
                }
                for (final String key : hiderSection.getKeys(false)) {
                    final String rawConditionsSource = hiderSection.getString(key + ".source_player");
                    final String rawConditionsTarget = hiderSection.getString(key + ".target_player");
                    hiders.put(getConditions(instructions, pack, key, rawConditionsSource),
                            getConditions(instructions, pack, key, rawConditionsTarget));
                }
            }
        } catch (final QuestException e) {
            log.error("Could not load player_hider: %s".formatted(e.getMessage()), e);
        }
    }

    /**
     * Starts the {@link PlayerHider}.
     *
     * @param config the config accessor
     */
    public void start(final ConfigAccessor config) {
        final long updateInterval = config.getLong("hider.player_update_interval", 20);
        if (updateInterval <= 0) {
            return;
        }
        bukkitTask = Scheduling.runGlobalTimer(plugin, task -> updateVisibility(), 1, updateInterval);
    }

    /**
     * Stops the running {@link PlayerHider}.
     */
    public void stop() {
        if (bukkitTask == null) {
            log.debug("Attempted to stop PlayerHider, but it was not running.");
            return;
        }
        bukkitTask.cancel();
    }

    private Collection<ConditionIdentifier> getConditions(final Instructions instructions, final QuestPackage pack, final String key,
                                                          @Nullable final String rawConditions) throws QuestException {
        if (rawConditions == null || rawConditions.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return instructions.createForArgument(pack, rawConditions).identifier(ConditionIdentifier.class)
                    .list().get().getValue(null);
        } catch (final QuestException e) {
            throw new QuestException("Error while loading conditions for player_hider '" + key + "' in Package '" + pack.getQuestPath() + "': " + e.getMessage(), e);
        }
    }

    /**
     * Trigger an update for the visibility.
     */
    public void updateVisibility() {
        final List<OnlineProfile> onlineProfiles = List.copyOf(profileProvider.getOnlineProfiles());
        updateVisibility(onlineProfiles, onlineProfiles);
    }

    /**
     * Trigger an update for the visibility.
     *
     * @param profile the profile to update the visibility for
     */
    public void updateVisibility(final OnlineProfile profile) {
        updateVisibility(List.copyOf(profileProvider.getOnlineProfiles()), List.of(profile));
    }

    /**
     * Checks the hider conditions of every profile on the thread owning its player, then applies the visibility
     * for each source on the thread owning the source.
     * On Paper everything runs directly on the main thread.
     *
     * @param targets the profiles that may be hidden
     * @param sources the profiles whose view is updated
     */
    private void updateVisibility(final List<OnlineProfile> targets, final List<OnlineProfile> sources) {
        final List<Map.Entry<Collection<ConditionIdentifier>, Collection<ConditionIdentifier>>> hiderList = List.copyOf(hiders.entrySet());
        final Set<OnlineProfile> targetSet = Set.copyOf(targets);
        final Set<OnlineProfile> sourceSet = Set.copyOf(sources);
        final Set<OnlineProfile> involved = new LinkedHashSet<>(targets);
        involved.addAll(sources);
        ProfileThreads.supplyEach(plugin, involved,
                        profile -> checkHiders(profile, hiderList, targetSet.contains(profile), sourceSet.contains(profile)))
                .thenAccept(matches -> {
                    final Map<OnlineProfile, List<OnlineProfile>> profilesToHide = getProfilesToHide(hiderList, matches, targets, sources);
                    for (final OnlineProfile source : sources) {
                        final List<OnlineProfile> toHide = profilesToHide.get(source);
                        Scheduling.executeForEntity(plugin, source.getPlayer(), () -> updateVisibilityForProfiles(targets, source, toHide));
                    }
                });
    }

    private HiderMatches checkHiders(final OnlineProfile profile,
                                     final List<Map.Entry<Collection<ConditionIdentifier>, Collection<ConditionIdentifier>>> hiderList,
                                     final boolean isTarget, final boolean isSource) {
        final boolean[] asTarget = new boolean[hiderList.size()];
        final boolean[] asSource = new boolean[hiderList.size()];
        for (int i = 0; i < hiderList.size(); i++) {
            final Map.Entry<Collection<ConditionIdentifier>, Collection<ConditionIdentifier>> hider = hiderList.get(i);
            asTarget[i] = isTarget && conditionManager.testAll(profile, hider.getValue());
            asSource[i] = isSource && conditionManager.testAll(profile, hider.getKey());
        }
        return new HiderMatches(asTarget, asSource);
    }

    private void updateVisibilityForProfiles(final Collection<? extends OnlineProfile> onlineProfiles, final OnlineProfile source,
                                             @Nullable final List<OnlineProfile> profilesToHide) {
        if (profilesToHide == null) {
            for (final OnlineProfile target : onlineProfiles) {
                source.getPlayer().showPlayer(plugin, target.getPlayer());
            }
        } else {
            for (final OnlineProfile target : onlineProfiles) {
                if (profilesToHide.contains(target)) {
                    source.getPlayer().hidePlayer(plugin, target.getPlayer());
                } else {
                    source.getPlayer().showPlayer(plugin, target.getPlayer());
                }
            }
        }
    }

    private Map<OnlineProfile, List<OnlineProfile>> getProfilesToHide(
            final List<Map.Entry<Collection<ConditionIdentifier>, Collection<ConditionIdentifier>>> hiderList,
            final Map<OnlineProfile, HiderMatches> matches, final List<OnlineProfile> targets, final List<OnlineProfile> sources) {
        final Map<OnlineProfile, List<OnlineProfile>> profilesToHide = new HashMap<>();
        for (int i = 0; i < hiderList.size(); i++) {
            final int hider = i;
            final List<OnlineProfile> targetProfiles = new ArrayList<>();
            for (final OnlineProfile target : targets) {
                final HiderMatches match = matches.get(target);
                if (match != null && match.asTarget()[hider]) {
                    targetProfiles.add(target);
                }
            }
            for (final OnlineProfile source : sources) {
                final HiderMatches match = matches.get(source);
                if (match == null || !match.asSource()[hider]) {
                    continue;
                }
                final List<OnlineProfile> hiddenProfiles = getOrCreateProfileList(source, profilesToHide);
                hiddenProfiles.addAll(targetProfiles);
                hiddenProfiles.remove(source);
                profilesToHide.put(source, hiddenProfiles);
            }
        }
        return profilesToHide;
    }

    private List<OnlineProfile> getOrCreateProfileList(final OnlineProfile onlineProfile, final Map<OnlineProfile, List<OnlineProfile>> profilesToHide) {
        final List<OnlineProfile> profiles = profilesToHide.get(onlineProfile);
        return profiles == null ? new ArrayList<>() : profiles;
    }

    /**
     * Which hiders a profile matches, by index in the hider list.
     *
     * @param asTarget whether the profile meets the target conditions of each hider
     * @param asSource whether the profile meets the source conditions of each hider
     */
    @SuppressWarnings("PMD.ArrayIsStoredDirectly")
    private record HiderMatches(boolean[] asTarget, boolean[] asSource) {
    }
}
