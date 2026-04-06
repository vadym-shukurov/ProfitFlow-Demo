package com.profitflow.application.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable result of a completed ABC allocation run, suitable for serialisation
 * to the REST API response.
 *
 * @param activityCosts          total allocated cost per activity ID (sum of all resource inputs)
 * @param productCosts           total allocated cost per product ID (sum of all activity inputs)
 * @param flows                  ordered list of individual cost-flow edges for Sankey visualisation
 * @param unallocatedResourceIds IDs of resources that had no matching Activity rule and were skipped
 */
public record AllocationRunResult(
        Map<String, BigDecimal> activityCosts,
        Map<String, BigDecimal> productCosts,
        List<AllocationFlowDto> flows,
        List<String> unallocatedResourceIds
) {
    public AllocationRunResult {
        activityCosts = Map.copyOf(Objects.requireNonNull(activityCosts));
        productCosts = Map.copyOf(Objects.requireNonNull(productCosts));
        flows = List.copyOf(Objects.requireNonNull(flows));
        unallocatedResourceIds = List.copyOf(Objects.requireNonNull(unallocatedResourceIds));
    }
}
