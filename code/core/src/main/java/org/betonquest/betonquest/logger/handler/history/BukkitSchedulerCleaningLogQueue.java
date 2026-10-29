package org.betonquest.betonquest.logger.handler.history;

import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.InstantSource;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.LogRecord;

/**
 * LogRecord Queue that drops expired records when {@link #removeExpiredRecords()} is called regularly.
 */
public class BukkitSchedulerCleaningLogQueue extends QueueBackedLogRecordQueue {

    /**
     * Time that new log records are valid for.
     */
    private final Duration validFor;

    /**
     * Instant source to get the current time when comparing {@link LogRecord}s.
     */
    private final InstantSource instantSource;

    /**
     * Create a log record queue cleaned by a repeating task. Records are not dropped until
     * {@link #removeExpiredRecords()} is called.
     *
     * @param instantSource instant source
     * @param validFor      duration that log records should be valid for
     */
    public BukkitSchedulerCleaningLogQueue(final InstantSource instantSource, final Duration validFor) {
        super(new ConcurrentLinkedQueue<>());
        this.validFor = validFor;
        this.instantSource = instantSource;
    }

    /**
     * Removes the expired entries from the log queue.
     * Meant to be called regularly from a repeating task, see {@link org.betonquest.betonquest.logger.HandlerFactory}.
     */
    public void removeExpiredRecords() {
        while (isExpired(getRecordQueue().peek())) {
            getRecordQueue().remove();
        }
    }

    private boolean isExpired(@Nullable final LogRecord record) {
        return record != null && record.getInstant().isBefore(instantSource.instant().minus(validFor));
    }
}
