package com.profitflow.application.service;

import com.profitflow.application.model.ActivityProductRuleRow;
import com.profitflow.application.model.ResourceActivityRuleRow;
import com.profitflow.application.port.in.AllocationRuleUseCase;
import com.profitflow.application.port.out.ActivityProductRuleRepositoryPort;
import com.profitflow.application.port.out.ResourceActivityRuleRepositoryPort;
import com.profitflow.application.audit.AuditedOperation;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Application service for managing the two sets of ABC allocation rules.
 *
 * <p>Rule persistence is delegated to the outbound repository ports;
 * this service adds audit logging via AOP for every write operation.
 */
@Service
public class AllocationRuleService implements AllocationRuleUseCase {

    private final ResourceActivityRuleRepositoryPort resourceActivityRules;
    private final ActivityProductRuleRepositoryPort activityProductRules;

    /**
     * @param resourceActivityRules port for Resource → Activity rule persistence
     * @param activityProductRules  port for Activity → Product rule persistence
     */
    public AllocationRuleService(
            ResourceActivityRuleRepositoryPort resourceActivityRules,
            ActivityProductRuleRepositoryPort activityProductRules) {
        this.resourceActivityRules = resourceActivityRules;
        this.activityProductRules = activityProductRules;
    }

    /** {@inheritDoc} */
    @Override
    public List<ResourceActivityRuleRow> listResourceToActivityRules() {
        return resourceActivityRules.findAllRows();
    }

    /** {@inheritDoc} */
    @Override
    @AuditedOperation(action = "RESOURCE_ACTIVITY_RULES_REPLACED", entityType = "AllocationRule",
                      critical = true)
    public void replaceResourceToActivityRules(List<ResourceActivityRuleRow> rules) {
        resourceActivityRules.replaceAll(rules == null ? List.of() : rules);
    }

    /** {@inheritDoc} */
    @Override
    public List<ActivityProductRuleRow> listActivityToProductRules() {
        return activityProductRules.findAllRows();
    }

    /** {@inheritDoc} */
    @Override
    @AuditedOperation(action = "ACTIVITY_PRODUCT_RULES_REPLACED", entityType = "AllocationRule",
                      critical = true)
    public void replaceActivityToProductRules(List<ActivityProductRuleRow> rules) {
        activityProductRules.replaceAll(rules == null ? List.of() : rules);
    }
}
