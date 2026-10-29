package org.betonquest.betonquest.thread;

import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.common.function.QuestSupplier;
import org.betonquest.betonquest.api.profile.OnlineProfile;
import org.betonquest.betonquest.api.profile.Profile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Finds and uses the server thread responsible for a profile.
 * <p>
 * For an online profile that is the thread owning the player (the main thread on Paper, the player's region
 * thread on Folia). For a missing or offline profile it is any server thread, falling back to the global region.
 */
public final class ProfileThreads {

    private ProfileThreads() {
    }

    /**
     * Gets the online player of a profile.
     *
     * @param profile the profile, may be null
     * @return the online player or null
     */
    @Nullable
    public static Player onlinePlayer(@Nullable final Profile profile) {
        if (profile == null) {
            return null;
        }
        return profile.getOnlineProfile().map(OnlineProfile::getPlayer).orElse(null);
    }

    /**
     * Checks if the current thread is a server tick thread.
     * Blocking such a thread on another server thread risks a deadlock.
     *
     * @return true when called from a server tick thread
     */
    public static boolean isServerThread() {
        return Bukkit.isPrimaryThread();
    }

    /**
     * Checks if the current thread may touch the game state of the profile.
     *
     * @param profile the profile, may be null
     * @return true if work for the profile may run directly on this thread
     */
    public static boolean isOwnedByCurrentThread(@Nullable final Profile profile) {
        final Player player = onlinePlayer(profile);
        if (player != null) {
            return Bukkit.isOwnedByCurrentRegion(player);
        }
        return Bukkit.isPrimaryThread();
    }

    /**
     * Computes a value on the server thread responsible for the profile.
     * If the player leaves before the task ran, it runs on the global region instead, the same way it would run
     * for an offline profile.
     *
     * @param plugin  the owning plugin
     * @param profile the profile, may be null
     * @param task    the task
     * @param <T>     the result type
     * @return a future completing with the result
     */
    public static <T> CompletableFuture<T> supply(final Plugin plugin, @Nullable final Profile profile, final Supplier<T> task) {
        final Player player = onlinePlayer(profile);
        if (player == null) {
            return Scheduling.supplyGlobal(plugin, task);
        }
        final CompletableFuture<T> future = new CompletableFuture<>();
        final Runnable complete = () -> complete(future, task);
        if (Bukkit.isOwnedByCurrentRegion(player)) {
            complete.run();
            return future;
        }
        final Runnable onGlobal = () -> Bukkit.getGlobalRegionScheduler().execute(plugin, complete);
        if (player.getScheduler().run(plugin, scheduled -> complete.run(), onGlobal) == null) {
            onGlobal.run();
        }
        return future;
    }

    /**
     * Computes a value on the server thread responsible for the profile and waits for it.
     * Must not be called from a server thread that does not own the profile, as waiting there can deadlock.
     *
     * @param plugin  the owning plugin
     * @param profile the profile, may be null
     * @param task    the task
     * @param <T>     the result type
     * @return the result
     * @throws QuestException if the task failed or the waiting thread was interrupted
     */
    @SuppressWarnings("PMD.DoNotUseThreads")
    public static <T> T call(final Plugin plugin, @Nullable final Profile profile, final QuestSupplier<T> task) throws QuestException {
        try {
            return supply(plugin, profile, () -> {
                try {
                    return task.get();
                } catch (final QuestException e) {
                    throw new CompletionException(e);
                }
            }).get();
        } catch (final ExecutionException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof QuestException) {
                throw new QuestException(cause.getMessage(), e);
            }
            throw new QuestException("Failed to run on the server thread: " + cause.getMessage(), e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QuestException("Interrupted while waiting for the server thread", e);
        }
    }

    /**
     * Runs a task on the server thread responsible for the profile, directly if this is that thread.
     * If the player leaves before the task ran, it runs on the global region instead.
     *
     * @param plugin  the owning plugin
     * @param profile the profile, may be null
     * @param task    the task
     */
    public static void execute(final Plugin plugin, @Nullable final Profile profile, final Runnable task) {
        final Player player = onlinePlayer(profile);
        if (player == null) {
            if (Bukkit.isPrimaryThread()) {
                task.run();
            } else {
                Bukkit.getGlobalRegionScheduler().execute(plugin, task);
            }
            return;
        }
        if (Bukkit.isOwnedByCurrentRegion(player)) {
            task.run();
            return;
        }
        final Runnable onGlobal = () -> Bukkit.getGlobalRegionScheduler().execute(plugin, task);
        if (player.getScheduler().run(plugin, scheduled -> task.run(), onGlobal) == null) {
            onGlobal.run();
        }
    }

    /**
     * Runs a task for each online profile on the thread owning its player.
     * Runs directly for players owned by the current thread and schedules the others.
     *
     * @param plugin   the owning plugin
     * @param profiles the profiles
     * @param task     the task to run per profile
     */
    public static void forEach(final Plugin plugin, final Collection<? extends OnlineProfile> profiles, final Consumer<OnlineProfile> task) {
        for (final OnlineProfile profile : profiles) {
            Scheduling.executeForEntity(plugin, profile.getPlayer(), () -> task.accept(profile));
        }
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private static <T> void complete(final CompletableFuture<T> future, final Supplier<T> task) {
        try {
            future.complete(task.get());
        } catch (final RuntimeException e) {
            future.completeExceptionally(e);
        }
    }
}
