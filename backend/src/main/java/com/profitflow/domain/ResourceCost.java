package com.profitflow.domain;

import java.util.Objects;

/**
 * A raw general-ledger style cost line (resource pool) before allocation.
 */
public record ResourceCost(String id, String label, Money amount) {

    public ResourceCost {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(amount, "amount");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }
}
