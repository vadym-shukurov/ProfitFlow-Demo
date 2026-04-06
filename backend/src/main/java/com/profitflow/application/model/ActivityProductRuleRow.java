package com.profitflow.application.model;

import java.math.BigDecimal;

/**
 * Flat data transfer object representing a single Activity → Product allocation rule.
 *
 * <p>The {@code driverWeight} is a relative weight: the engine normalises all weights
 * for a given activity to sum to 1.0 before applying proportional allocation.
 *
 * @param activityId   UUID string of the activity cost pool to allocate from
 * @param productId    UUID string of the target product
 * @param driverWeight relative allocation weight (positive, non-zero)
 */
public record ActivityProductRuleRow(String activityId, String productId, BigDecimal driverWeight) {
}
