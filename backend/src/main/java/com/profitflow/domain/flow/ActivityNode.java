package com.profitflow.domain.flow;

/**
 * Domain value object representing an activity node in the cost-flow graph.
 *
 * <p>Activity nodes are the intermediate stage in the two-stage ABC model:
 * resource costs flow <em>into</em> activities, then activity costs flow
 * <em>out to</em> products.
 *
 * @param id non-blank UUID string identifying the activity
 */
public record ActivityNode(String id) implements FlowNode {

    public ActivityNode {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }
}
