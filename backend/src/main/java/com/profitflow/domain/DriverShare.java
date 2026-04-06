package com.profitflow.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Relative weight of an allocation target for proportional (driver-based) spreading.
 */
public record DriverShare(String targetId, BigDecimal weight) {

    public DriverShare {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(weight, "weight");
        if (targetId.isBlank()) {
            throw new IllegalArgumentException("targetId must not be blank");
        }
        if (weight.signum() < 0) {
            throw new IllegalArgumentException("weight must be non-negative");
        }
    }
}
