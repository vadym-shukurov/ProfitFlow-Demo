package com.profitflow.domain.flow;

/**
 * Discriminator for the three node types in the ABC cost-flow graph.
 *
 * <p>Kept in the domain layer so that application and adapter layers can serialise
 * node types without depending on the concrete record types.
 */
public enum FlowNodeKind {

    /** A general-ledger resource cost (source of the flow graph). */
    RESOURCE,

    /** An intermediate activity / cost-pool. */
    ACTIVITY,

    /** A final product, service, or customer segment (sink of the flow graph). */
    PRODUCT
}
