package com.profitflow.adapter.in.web;

import com.profitflow.application.exception.ResourceNotFoundException;
import com.profitflow.application.model.AllocationRunResult;
import com.profitflow.application.model.AllocationRunSummary;
import com.profitflow.application.port.in.AllocationRunQueryUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only REST adapter for persisted allocation run history.
 */
@RestController
@RequestMapping("/api/v1/allocations")
public class AllocationRunQueryController {

    private final AllocationRunQueryUseCase query;

    public AllocationRunQueryController(AllocationRunQueryUseCase query) {
        this.query = query;
    }

    /**
     * Lists recent allocation runs (metadata only). Default limit 20, max 100.
     */
    @GetMapping("/runs")
    @PreAuthorize("hasAnyRole('FINANCE_MANAGER', 'ADMIN')")
    public List<AllocationRunSummary> listRuns(
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return query.listRecentRuns(limit);
    }

    /**
     * Returns the stored full result for a single run number.
     */
    @GetMapping("/runs/{runNumber}")
    @PreAuthorize("hasAnyRole('FINANCE_MANAGER', 'ADMIN')")
    public AllocationRunResult getRun(@PathVariable long runNumber) {
        return query.getRun(runNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Allocation run not found: " + runNumber));
    }
}
