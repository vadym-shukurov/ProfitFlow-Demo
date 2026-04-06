package com.profitflow.application.port.in;

import com.profitflow.application.model.AllocationRunResult;

/**
 * Inbound port for executing a full Activity-Based Costing allocation run.
 *
 * <p>A single call to {@link #runAllocation()} reads the current resource costs
 * and all configured allocation rules, runs the two-stage proportional allocator,
 * and returns an immutable result containing per-activity and per-product costs
 * together with the full cost-flow graph.
 *
 * <p>The operation is read-only at the domain level; it does not persist the result.
 */
public interface AllocationRunUseCase {

    /**
     * Runs the ABC allocation algorithm against the current cost and rule data.
     *
     * @return the computed allocation result with flows and cost maps
     */
    AllocationRunResult runAllocation();
}
