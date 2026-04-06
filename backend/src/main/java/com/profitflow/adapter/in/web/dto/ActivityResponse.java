package com.profitflow.adapter.in.web.dto;

/**
 * API response payload representing an activity (cost pool) in the ABC model.
 *
 * <p>Activities are intermediate cost objects: resource costs flow
 * <em>into</em> activities via resource-to-activity allocation rules,
 * and activity costs then flow <em>out</em> to products via
 * activity-to-product rules.
 *
 * @param id   unique identifier (UUID string)
 * @param name human-readable display name
 */
public record ActivityResponse(String id, String name) {
}
