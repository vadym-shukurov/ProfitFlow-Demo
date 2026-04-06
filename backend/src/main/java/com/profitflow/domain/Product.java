package com.profitflow.domain;

import java.util.Objects;

/**
 * Final cost object (product or service) receiving fully loaded costs.
 */
public record Product(String id, String name) {

    public Product {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }
}
