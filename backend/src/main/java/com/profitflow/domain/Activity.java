package com.profitflow.domain;

import java.util.Objects;

/**
 * An activity (process/cost pool) in the ABC model.
 */
public record Activity(String id, String name) {

    public Activity {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }
}
