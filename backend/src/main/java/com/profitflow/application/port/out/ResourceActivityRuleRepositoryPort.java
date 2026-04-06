package com.profitflow.application.port.out;

import com.profitflow.application.model.ResourceActivityRuleRow;

import java.util.List;

/**
 * Outbound port for persisting Resource → Activity allocation rules.
 *
 * <p>The replace operation is expected to be atomic: implementations should
 * delete all existing rows and insert the new set within a single transaction.
 */
public interface ResourceActivityRuleRepositoryPort {

    /** Returns all current Resource → Activity rules in persistence order. */
    List<ResourceActivityRuleRow> findAllRows();

    /**
     * Atomically replaces the entire rule set with the provided list.
     *
     * @param rules new rule set; must not be {@code null} (pass empty list to clear)
     */
    void replaceAll(List<ResourceActivityRuleRow> rules);
}
