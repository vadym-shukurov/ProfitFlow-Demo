package com.profitflow.application.model;

import java.math.BigDecimal;

/**
 * Flat data transfer object representing a single Resource → Activity allocation rule.
 *
 * <p>The {@code driverWeight} is a relative weight: the engine normalises all weights
 * for a given resource to sum to 1.0 before applying proportional allocation.
 *
 * @param resourceId   UUID string of the resource cost to allocate from
 * @param activityId   UUID string of the target activity
 * @param driverWeight relative allocation weight (positive, non-zero)
 */
public record ResourceActivityRuleRow(String resourceId, String activityId, BigDecimal driverWeight) {
}
