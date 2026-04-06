package com.profitflow.domain.flow;

import com.profitflow.domain.Money;

import java.util.Objects;

/**
 * A directed monetary flow between two nodes (for Sankey and audit).
 */
public record CostFlow(FlowNode from, FlowNode to, Money amount) {

    public CostFlow {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(amount, "amount");
    }
}
