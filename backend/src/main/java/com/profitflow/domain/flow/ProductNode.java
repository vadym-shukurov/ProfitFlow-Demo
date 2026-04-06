package com.profitflow.domain.flow;

/**
 * Domain value object representing a product node in the cost-flow graph.
 *
 * <p>Product nodes are the final stage in the two-stage ABC model: activity
 * costs flow into products to produce the per-product cost breakdown that
 * is displayed on the CFO dashboard.
 *
 * @param id non-blank UUID string identifying the product
 */
public record ProductNode(String id) implements FlowNode {

    public ProductNode {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }
}
