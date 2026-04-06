package com.profitflow.domain;

import com.profitflow.domain.flow.CostFlow;

import java.util.List;
import java.util.Map;

/**
 * Outcome of a full two-stage ABC allocation run.
 */
public record AllocationResult(
        Map<String, Money> activityCosts,
        Map<String, Money> productCosts,
        List<CostFlow> flows
) {
}
