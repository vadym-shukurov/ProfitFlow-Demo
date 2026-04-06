package com.profitflow.application.port.in;

import com.profitflow.application.model.AllocationRunResult;
import com.profitflow.application.model.AllocationRunSummary;

import java.util.List;
import java.util.Optional;

/**
 * Inbound port for read-only access to persisted allocation run history.
 */
public interface AllocationRunQueryUseCase {

    /** Most recent runs first; {@code limit} is clamped by the implementation. */
    List<AllocationRunSummary> listRecentRuns(int limit);

    /** Full result snapshot for a run number. */
    Optional<AllocationRunResult> getRun(long runNumber);
}
