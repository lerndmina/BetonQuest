package org.betonquest.betonquest.thread;

/**
 * A scheduled task that can be cancelled.
 * Hides the scheduler implementation, so callers do not depend on the Paper scheduler API.
 */
@FunctionalInterface
public interface TaskHandle {

    /**
     * Cancels the task. Running executions finish, future ones do not start.
     */
    void cancel();
}
