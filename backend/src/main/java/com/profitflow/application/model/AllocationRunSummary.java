package com.profitflow.application.model;

import java.time.Instant;

/**
 * Lightweight view of a persisted allocation run for list endpoints (no full result JSON).
 *
 * @param runNumber          monotonic run identifier
 * @param executedAt         UTC instant when the run completed
 * @param executedBy         authenticated principal or {@code system}
 * @param inputSnapshotHash  SHA-256 hex of canonical inputs used for the run
 */
public record AllocationRunSummary(
        long runNumber,
        Instant executedAt,
        String executedBy,
        String inputSnapshotHash
) {
}
