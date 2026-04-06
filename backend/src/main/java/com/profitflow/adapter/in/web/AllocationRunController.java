package com.profitflow.adapter.in.web;

import com.profitflow.application.model.AllocationRunResult;
import com.profitflow.application.port.in.AllocationRunUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter that triggers a full ABC allocation run and returns the result.
 *
 * <p>The run is idempotent with respect to the current database state: repeated calls
 * return the same result (modulo concurrent data changes) without mutating the database.
 * It is modelled as {@code POST} rather than {@code GET} because the computation
 * is expensive and clients should express intent rather than trigger it on every cache miss.
 */
@RestController
@RequestMapping("/api/v1/allocations")
public class AllocationRunController {

    private final AllocationRunUseCase allocationRun;

    public AllocationRunController(AllocationRunUseCase allocationRun) {
        this.allocationRun = allocationRun;
    }

    /**
     * Executes the two-stage ABC allocation and returns the cost-flow result.
     *
     * @return {@link AllocationRunResult} containing per-activity and per-product
     *         cost totals, the directed flow graph, and a list of resource IDs that
     *         had no allocation rules configured
     */
    @PostMapping("/run")
    @PreAuthorize("hasAnyRole('FINANCE_MANAGER', 'ADMIN')")
    public AllocationRunResult run() {
        return allocationRun.runAllocation();
    }
}
