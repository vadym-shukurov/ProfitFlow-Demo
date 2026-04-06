package com.profitflow.application.service;

import com.profitflow.application.model.AllocationRunResult;
import com.profitflow.application.model.AllocationRunSummary;
import com.profitflow.application.port.in.AllocationRunQueryUseCase;
import com.profitflow.application.port.out.AllocationRunReadPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Application service for allocation run history queries.
 */
@Service
public class AllocationRunQueryService implements AllocationRunQueryUseCase {

    private static final int MAX_LIMIT = 100;

    private final AllocationRunReadPort readPort;

    public AllocationRunQueryService(AllocationRunReadPort readPort) {
        this.readPort = readPort;
    }

    @Override
    public List<AllocationRunSummary> listRecentRuns(int limit) {
        int capped = Math.min(Math.max(limit, 1), MAX_LIMIT);
        return readPort.findRecent(capped);
    }

    @Override
    public Optional<AllocationRunResult> getRun(long runNumber) {
        return readPort.findResultByRunNumber(runNumber);
    }
}
