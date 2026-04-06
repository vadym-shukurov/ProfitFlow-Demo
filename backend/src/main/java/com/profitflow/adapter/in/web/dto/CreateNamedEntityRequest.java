package com.profitflow.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a named entity (Activity or Product).
 *
 * <p>The {@code @Size} constraint mirrors the database column length to
 * produce a clean validation error rather than a database truncation error.
 */
public record CreateNamedEntityRequest(
        @NotBlank @Size(max = 256) String name) {
}
