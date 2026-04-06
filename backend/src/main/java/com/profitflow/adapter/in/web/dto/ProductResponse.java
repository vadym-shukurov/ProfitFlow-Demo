package com.profitflow.adapter.in.web.dto;

/**
 * API response payload representing a product in the cost catalogue.
 *
 * <p>Products are the final cost objects (sinks) in the ABC model.
 * Their loaded unit costs represent total spend attributable to delivering
 * each product or service line.
 *
 * @param id   unique identifier (UUID string)
 * @param name human-readable display name
 */
public record ProductResponse(String id, String name) {
}
