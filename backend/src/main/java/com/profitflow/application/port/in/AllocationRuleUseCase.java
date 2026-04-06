package com.profitflow.application.port.in;

import com.profitflow.application.model.ActivityProductRuleRow;
import com.profitflow.application.model.ResourceActivityRuleRow;

import java.util.List;

/**
 * Inbound port for managing the two sets of ABC allocation rules:
 * Resource → Activity and Activity → Product.
 *
 * <p>Rule replacement is atomic per set — the full set is deleted and rewritten
 * in a single transaction, preventing partial updates.
 */
public interface AllocationRuleUseCase {

    /** Returns all current Resource → Activity driver rules in persistence order. */
    List<ResourceActivityRuleRow> listResourceToActivityRules();

    /**
     * Atomically replaces the full set of Resource → Activity rules.
     *
     * @param rules new rule set; {@code null} is treated as an empty list
     */
    void replaceResourceToActivityRules(List<ResourceActivityRuleRow> rules);

    /** Returns all current Activity → Product driver rules in persistence order. */
    List<ActivityProductRuleRow> listActivityToProductRules();

    /**
     * Atomically replaces the full set of Activity → Product rules.
     *
     * @param rules new rule set; {@code null} is treated as an empty list
     */
    void replaceActivityToProductRules(List<ActivityProductRuleRow> rules);
}
