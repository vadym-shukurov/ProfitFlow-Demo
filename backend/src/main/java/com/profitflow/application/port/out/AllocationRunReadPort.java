package com.profitflow.application.port.out;

import com.profitflow.application.model.AllocationRunResult;
import com.profitflow.application.model.AllocationRunSummary;

import java.util.List;
import java.util.Optional;

/**
 * Read-only persistence access to stored allocation run history.
 */
public interface AllocationRunReadPort {

    /**
     * Returns the most recent runs, newest first.
     *
     * @param limit capped by the caller (controllers should bound this)
     */
    List<AllocationRunSummary> findRecent(int limit);

    /** Returns the full stored result for a run number, if present. */
    Optional<AllocationRunResult> findResultByRunNumber(long runNumber);
}
