package org.betonquest.betonquest.thread;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.common.function.QuestRunnable;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Schedules work on the thread that owns the touched game state.
 * <p>
 * Uses Paper's region, entity, global and async schedulers. On Paper (and Purpur) every one of the synchronous
 * schedulers runs on the main thread, so the behaviour there is the same as with the Bukkit scheduler. On Folia
 * the work runs on the region thread owning the entity or location, or on the global region thread for
 * world-agnostic state.
 * <p>
 * Tick values are game ticks. A delay below one tick is raised to one tick for the synchronous schedulers,
 * which matches the Bukkit scheduler running a zero delay task on the next tick.
 */
@SuppressWarnings({"PMD.TooManyMethods", "PMD.GodClass"})
public final class Scheduling {

    /**
     * Milliseconds per game tick.
     */
    private static final long MILLIS_PER_TICK = 50L;

    /**
     * Handle to {@code Server#isGlobalTickThread()}, null on servers before 1.21 that do not have it.
     */
    @Nullable
    private static final MethodHandle IS_GLOBAL_TICK_THREAD = findIsGlobalTickThread();

    private Scheduling() {
    }

    @Nullable
    private static MethodHandle findIsGlobalTickThread() {
        try {
            return MethodHandles.publicLookup().findVirtual(Server.class, "isGlobalTickThread", MethodType.methodType(boolean.class));
        } catch (final NoSuchMethodException | IllegalAccessException e) {
            return null;
        }
    }

    /**
     * Checks if the current thread is the global region thread.
     * On Paper that is the main thread, on Folia the thread ticking world-agnostic state.
     *
     * @return true if called from the global region thread
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    public static boolean isGlobalTickThread() {
        if (IS_GLOBAL_TICK_THREAD == null) {
            return Bukkit.isPrimaryThread();
        }
        try {
            return (boolean) IS_GLOBAL_TICK_THREAD.invoke(Bukkit.getServer());
        } catch (final Throwable e) {
            return Bukkit.isPrimaryThread();
        }
    }

    /**
     * Runs a task on the global region on the next tick.
     *
     * @param plugin the owning plugin
     * @param task   the task
     * @return the task handle
     */
    public static TaskHandle runGlobal(final Plugin plugin, final Runnable task) {
        return wrap(Bukkit.getGlobalRegionScheduler().run(plugin, scheduled -> task.run()));
    }

    /**
     * Runs a task on the global region after a delay.
     *
     * @param plugin the owning plugin
     * @param task   the task
     * @param delay  the delay in ticks
     * @return the task handle
     */
    public static TaskHandle runGlobalLater(final Plugin plugin, final Runnable task, final long delay) {
        return wrap(Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduled -> task.run(), atLeastOneTick(delay)));
    }

    /**
     * Runs a task on the global region after a delay, handing it its own task handle.
     *
     * @param plugin the owning plugin
     * @param task   the task
     * @param delay  the delay in ticks
     * @return the task handle
     */
    public static TaskHandle runGlobalLater(final Plugin plugin, final Consumer<TaskHandle> task, final long delay) {
        return wrap(Bukkit.getGlobalRegionScheduler().runDelayed(plugin, adapt(task), atLeastOneTick(delay)));
    }

    /**
     * Runs a repeating task on the global region.
     *
     * @param plugin the owning plugin
     * @param task   the task
     * @param delay  the initial delay in ticks
     * @param period the period in ticks
     * @return the task handle
     */
    public static TaskHandle runGlobalTimer(final Plugin plugin, final Consumer<TaskHandle> task, final long delay, final long period) {
        return wrap(Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, adapt(task), atLeastOneTick(delay), atLeastOneTick(period)));
    }

    /**
     * Runs a task off the server threads.
     *
     * @param plugin the owning plugin
     * @param task   the task
     * @return the task handle
     */
    public static TaskHandle runAsync(final Plugin plugin, final Runnable task) {
        return wrap(Bukkit.getAsyncScheduler().runNow(plugin, scheduled -> task.run()));
    }

    /**
     * Runs a task off the server threads after a delay.
     *
     * @param plugin the owning plugin
     * @param task   the task
     * @param delay  the delay in ticks
     * @return the task handle
     */
    public static TaskHandle runAsyncLater(final Plugin plugin, final Runnable task, final long delay) {
        if (delay <= 0) {
            return runAsync(plugin, task);
        }
        return wrap(Bukkit.getAsyncScheduler().runDelayed(plugin, scheduled -> task.run(), delay * MILLIS_PER_TICK, TimeUnit.MILLISECONDS));
    }

    /**
     * Runs a task off the server threads after a delay, handing it its own task handle.
     *
     * @param plugin the owning plugin
     * @param task   the task
     * @param delay  the delay in ticks
     * @return the task handle
     */
    public static TaskHandle runAsyncLater(final Plugin plugin, final Consumer<TaskHandle> task, final long delay) {
        if (delay <= 0) {
            return wrap(Bukkit.getAsyncScheduler().runNow(plugin, adapt(task)));
        }
        return wrap(Bukkit.getAsyncScheduler().runDelayed(plugin, adapt(task), delay * MILLIS_PER_TICK, TimeUnit.MILLISECONDS));
    }

    /**
     * Runs a repeating task off the server threads.
     *
     * @param plugin the owning plugin
     * @param task   the task
     * @param delay  the initial delay in ticks
     * @param period the period in ticks
     * @return the task handle
     */
    public static TaskHandle runAsyncTimer(final Plugin plugin, final Consumer<TaskHandle> task, final long delay, final long period) {
        return wrap(Bukkit.getAsyncScheduler().runAtFixedRate(plugin, adapt(task), Math.max(0, delay) * MILLIS_PER_TICK,
                atLeastOneTick(period) * MILLIS_PER_TICK, TimeUnit.MILLISECONDS));
    }

    /**
     * Runs a task on the thread owning the entity on the next tick.
     *
     * @param plugin the owning plugin
     * @param entity the entity the task works on
     * @param task   the task
     * @return the task handle, or null if the entity was already removed and the task will not run
     */
    @Nullable
    public static TaskHandle runForEntity(final Plugin plugin, final Entity entity, final Runnable task) {
        return wrapNullable(entity.getScheduler().run(plugin, scheduled -> task.run(), null));
    }

    /**
     * Runs a task on the thread owning the entity on the next tick.
     * If the entity is removed before that, for example a player logging out, the task runs on the global region.
     *
     * @param plugin the owning plugin
     * @param entity the entity the task works on
     * @param task   the task
     */
    public static void runForEntityOrGlobal(final Plugin plugin, final Entity entity, final Runnable task) {
        final Runnable onGlobal = () -> runGlobal(plugin, task);
        if (entity.getScheduler().run(plugin, scheduled -> task.run(), onGlobal) == null) {
            onGlobal.run();
        }
    }

    /**
     * Runs a task on the thread owning the entity after a delay.
     *
     * @param plugin the owning plugin
     * @param entity the entity the task works on
     * @param task   the task
     * @param delay  the delay in ticks
     * @return the task handle, or null if the entity was already removed and the task will not run
     */
    @Nullable
    public static TaskHandle runForEntityLater(final Plugin plugin, final Entity entity, final Runnable task, final long delay) {
        return wrapNullable(entity.getScheduler().runDelayed(plugin, scheduled -> task.run(), null, atLeastOneTick(delay)));
    }

    /**
     * Runs a task on the thread owning the entity after a delay.
     * If the entity is removed before that, for example a player logging out, the task runs on the global region.
     *
     * @param plugin the owning plugin
     * @param entity the entity the task works on
     * @param task   the task
     * @param delay  the delay in ticks
     */
    public static void runForEntityLaterOrGlobal(final Plugin plugin, final Entity entity, final Runnable task, final long delay) {
        final Runnable onGlobal = () -> runGlobal(plugin, task);
        if (entity.getScheduler().runDelayed(plugin, scheduled -> task.run(), onGlobal, atLeastOneTick(delay)) == null) {
            onGlobal.run();
        }
    }

    /**
     * Runs a repeating task on the thread owning the entity. The task stops when the entity is removed.
     *
     * @param plugin the owning plugin
     * @param entity the entity the task works on
     * @param task   the task
     * @param delay  the initial delay in ticks
     * @param period the period in ticks
     * @return the task handle, or null if the entity was already removed and the task will not run
     */
    @Nullable
    public static TaskHandle runForEntityTimer(final Plugin plugin, final Entity entity, final Consumer<TaskHandle> task,
                                                  final long delay, final long period) {
        return wrapNullable(entity.getScheduler().runAtFixedRate(plugin, adapt(task), null, atLeastOneTick(delay), atLeastOneTick(period)));
    }

    /**
     * Runs a repeating task on the thread owning the entity and runs a callback once the entity is removed.
     *
     * @param plugin  the owning plugin
     * @param entity  the entity the task works on
     * @param task    the task
     * @param retired the callback when the entity is removed, also called directly if it is already removed
     * @param delay   the initial delay in ticks
     * @param period  the period in ticks
     * @return the task handle, or null if the entity was already removed and the task will not run
     */
    @Nullable
    public static TaskHandle runForEntityTimer(final Plugin plugin, final Entity entity, final Consumer<TaskHandle> task,
                                               final Runnable retired, final long delay, final long period) {
        final ScheduledTask scheduled = entity.getScheduler().runAtFixedRate(plugin, adapt(task), retired,
                atLeastOneTick(delay), atLeastOneTick(period));
        if (scheduled == null) {
            retired.run();
            return null;
        }
        return wrap(scheduled);
    }

    /**
     * Runs a task now if the current thread owns the entity, otherwise on the owning thread on the next tick.
     *
     * @param plugin the owning plugin
     * @param entity the entity the task works on
     * @param task   the task
     */
    public static void executeForEntity(final Plugin plugin, final Entity entity, final Runnable task) {
        if (Bukkit.isOwnedByCurrentRegion(entity)) {
            task.run();
        } else {
            runForEntity(plugin, entity, task);
        }
    }

    /**
     * Runs a task on the thread owning the location on the next tick.
     *
     * @param plugin   the owning plugin
     * @param location the location the task works on
     * @param task     the task
     * @return the task handle
     */
    public static TaskHandle runAtLocation(final Plugin plugin, final Location location, final Runnable task) {
        return wrap(Bukkit.getRegionScheduler().run(plugin, location, scheduled -> task.run()));
    }

    /**
     * Runs a task now if the current thread owns the location, otherwise on the owning thread on the next tick.
     *
     * @param plugin   the owning plugin
     * @param location the location the task works on
     * @param task     the task
     */
    public static void executeAtLocation(final Plugin plugin, final Location location, final Runnable task) {
        if (Bukkit.isOwnedByCurrentRegion(location)) {
            task.run();
        } else {
            runAtLocation(plugin, location, task);
        }
    }

    /**
     * Runs quest logic now if the current thread owns the location, otherwise on the owning thread on the next tick.
     * A {@link QuestException} is thrown directly when running now, and logged when running later.
     *
     * @param plugin   the owning plugin
     * @param location the location the task works on
     * @param task     the task
     * @throws QuestException if the task runs now and fails
     */
    public static void runQuestAtLocation(final Plugin plugin, final Location location, final QuestRunnable task) throws QuestException {
        if (Bukkit.isOwnedByCurrentRegion(location)) {
            task.run();
            return;
        }
        runAtLocation(plugin, location, () -> runLogged(plugin, task));
    }

    /**
     * Runs quest logic now if the current thread is the global region thread, otherwise there on the next tick.
     * A {@link QuestException} is thrown directly when running now, and logged when running later.
     *
     * @param plugin the owning plugin
     * @param task   the task
     * @throws QuestException if the task runs now and fails
     */
    public static void runQuestGlobal(final Plugin plugin, final QuestRunnable task) throws QuestException {
        if (isGlobalTickThread()) {
            task.run();
            return;
        }
        runGlobal(plugin, () -> runLogged(plugin, task));
    }

    private static void runLogged(final Plugin plugin, final QuestRunnable task) {
        try {
            task.run();
        } catch (final QuestException e) {
            plugin.getLogger().log(Level.WARNING, "Error while running a scheduled quest task: " + e.getMessage(), e);
        }
    }

    /**
     * Computes a value on the global region thread.
     * Runs directly if the current thread is the global region thread.
     *
     * @param plugin the owning plugin
     * @param task   the task
     * @param <T>    the result type
     * @return a future completing with the result
     */
    public static <T> CompletableFuture<T> supplyGlobal(final Plugin plugin, final Supplier<T> task) {
        if (isGlobalTickThread()) {
            return completeWith(task);
        }
        final CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> complete(future, task));
        return future;
    }

    /**
     * Teleports an entity. Called from the thread owning the entity, or scheduled there.
     * The returned future completes on the thread owning the entity at its destination.
     *
     * @param plugin   the owning plugin
     * @param entity   the entity to teleport
     * @param location the target location
     * @return a future completing with whether the teleport succeeded
     */
    public static CompletableFuture<Boolean> teleport(final Plugin plugin, final Entity entity, final Location location) {
        if (Bukkit.isOwnedByCurrentRegion(entity)) {
            return entity.teleportAsync(location);
        }
        final CompletableFuture<Boolean> future = new CompletableFuture<>();
        final ScheduledTask scheduled = entity.getScheduler().run(plugin,
                ignored -> entity.teleportAsync(location).whenComplete((result, error) -> {
                    if (error == null) {
                        future.complete(result);
                    } else {
                        future.completeExceptionally(error);
                    }
                }), () -> future.complete(false));
        if (scheduled == null) {
            future.complete(false);
        }
        return future;
    }

    private static TaskHandle wrap(final ScheduledTask task) {
        return task::cancel;
    }

    @Nullable
    private static TaskHandle wrapNullable(@Nullable final ScheduledTask task) {
        return task == null ? null : task::cancel;
    }

    private static Consumer<ScheduledTask> adapt(final Consumer<TaskHandle> task) {
        return scheduled -> task.accept(scheduled::cancel);
    }

    private static <T> CompletableFuture<T> completeWith(final Supplier<T> task) {
        final CompletableFuture<T> future = new CompletableFuture<>();
        complete(future, task);
        return future;
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private static <T> void complete(final CompletableFuture<T> future, final Supplier<T> task) {
        try {
            future.complete(task.get());
        } catch (final RuntimeException e) {
            future.completeExceptionally(e);
        }
    }

    private static long atLeastOneTick(final long ticks) {
        return Math.max(1L, ticks);
    }
}
