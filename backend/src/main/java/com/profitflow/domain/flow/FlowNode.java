package com.profitflow.domain.flow;

/**
 * A typed node in the cost-flow graph, used to model the two-stage ABC flow
 * (Resource → Activity → Product) and power the Sankey visualisation.
 *
 * <p>The sealed hierarchy guarantees exhaustive handling in switch expressions without
 * a default branch, which prevents silent omissions when new node kinds are added.
 *
 * <p>The {@link #kind()} convenience method returns the {@link FlowNodeKind} enum
 * value, suitable for serialisation and client-facing APIs.
 */
public sealed interface FlowNode permits ResourceNode, ActivityNode, ProductNode {

    /** Returns the domain identifier of this node (UUID string for persisted entities). */
    String id();

    /**
     * Returns the discriminator enum for this node type.
     *
     * <p>Use this instead of raw {@code instanceof} checks when serialising to a
     * protocol that needs a type tag (e.g. the REST {@code fromKind/toKind} fields).
     */
    default FlowNodeKind kind() {
        return switch (this) {
            case ResourceNode ignored -> FlowNodeKind.RESOURCE;
            case ActivityNode ignored -> FlowNodeKind.ACTIVITY;
            case ProductNode  ignored -> FlowNodeKind.PRODUCT;
        };
    }
}
