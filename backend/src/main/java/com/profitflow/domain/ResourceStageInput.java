package com.profitflow.domain;

import java.util.List;
import java.util.Objects;

/**
 * Allocation of one resource line to activities using proportional driver weights.
 */
public record ResourceStageInput(ResourceCost resource, List<DriverShare> toActivities) {

    public ResourceStageInput {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(toActivities, "toActivities");
        if (toActivities.isEmpty()) {
            throw new IllegalArgumentException("toActivities must not be empty");
        }
    }
}
