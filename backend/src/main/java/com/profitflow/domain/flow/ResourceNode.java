package com.profitflow.domain.flow;

/**
 * Domain value object representing a resource cost node in the cost-flow graph.
 *
 * <p>Resource nodes are the entry points in the two-stage ABC model. Each
 * resource cost has a monetary amount that is proportionally distributed
 * to activity nodes according to the configured Resource → Activity rules.
 *
 * @param id non-blank UUID string identifying the resource cost
 */
public record ResourceNode(String id) implements FlowNode {

    public ResourceNode {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }
}
