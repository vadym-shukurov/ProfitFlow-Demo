package com.profitflow.application.model;

import java.math.BigDecimal;

/**
 * A single directed cost-flow edge from a source node to a target node.
 *
 * <p>Used to populate the Sankey diagram on the CFO dashboard. Each edge
 * represents a portion of cost flowing from a resource to an activity
 * (stage 1) or from an activity to a product (stage 2).
 *
 * @param fromKind    node kind of the source: {@code RESOURCE}, {@code ACTIVITY}, or {@code PRODUCT}
 * @param fromId      UUID string of the source node
 * @param toKind      node kind of the destination
 * @param toId        UUID string of the destination node
 * @param amount      monetary amount flowing along this edge
 * @param currencyCode ISO 4217 currency code of the amount
 */
public record AllocationFlowDto(
        String fromKind,
        String fromId,
        String toKind,
        String toId,
        BigDecimal amount,
        String currencyCode
) {
}
