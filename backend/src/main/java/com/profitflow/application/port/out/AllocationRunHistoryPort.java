package com.profitflow.application.port.out;

import com.profitflow.application.model.AllocationRunResult;

/**
 * Persists a versioned, immutable snapshot of each successful allocation run and
 * enqueues a domain event for downstream processors (outbox pattern).
 */
public interface AllocationRunHistoryPort {

    /**
     * Stores the run result and appends an {@code ALLOCATION_RUN_COMPLETED} outbox row
     * in the same transaction as the caller.
     *
     * @param executedBy          actor (from {@link CurrentUserPort})
     * @param inputSnapshotHash   SHA-256 hex of canonical inputs (costs + rules)
     * @param result              outcome returned to the client
     */
    void recordSuccessfulRun(String executedBy, String inputSnapshotHash, AllocationRunResult result);
}
