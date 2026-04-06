package com.profitflow.domain;

import java.util.List;
import java.util.Objects;

/**
 * Allocation of one activity's loaded cost to products using proportional driver weights.
 */
public record ActivityStageInput(String activityId, List<DriverShare> toProducts) {

    public ActivityStageInput {
        Objects.requireNonNull(activityId, "activityId");
        Objects.requireNonNull(toProducts, "toProducts");
        if (activityId.isBlank()) {
            throw new IllegalArgumentException("activityId must not be blank");
        }
        if (toProducts.isEmpty()) {
            throw new IllegalArgumentException("toProducts must not be empty");
        }
    }
}
