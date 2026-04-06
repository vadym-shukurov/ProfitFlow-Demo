package com.profitflow.adapter.out.persistence;

import com.profitflow.adapter.out.persistence.entity.ResourceActivityRuleEntity;
import com.profitflow.adapter.out.persistence.jpa.ActivityEntityRepository;
import com.profitflow.adapter.out.persistence.jpa.ResourceActivityRuleEntityRepository;
import com.profitflow.adapter.out.persistence.jpa.ResourceCostEntityRepository;
import com.profitflow.application.model.ResourceActivityRuleRow;
import com.profitflow.application.port.out.ResourceActivityRuleRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class ResourceActivityRulePersistenceAdapter implements ResourceActivityRuleRepositoryPort {

    private final ResourceActivityRuleEntityRepository ruleRepository;
    private final ResourceCostEntityRepository resourceRepository;
    private final ActivityEntityRepository activityRepository;

    public ResourceActivityRulePersistenceAdapter(
            ResourceActivityRuleEntityRepository ruleRepository,
            ResourceCostEntityRepository resourceRepository,
            ActivityEntityRepository activityRepository) {
        this.ruleRepository = ruleRepository;
        this.resourceRepository = resourceRepository;
        this.activityRepository = activityRepository;
    }

    @Override
    public List<ResourceActivityRuleRow> findAllRows() {
        return ruleRepository.findAllWithAssociations().stream()
                .map(r -> new ResourceActivityRuleRow(
                        r.getResource().getId().toString(),
                        r.getActivity().getId().toString(),
                        r.getDriverWeight()))
                .toList();
    }

    @Override
    @Transactional
    public void replaceAll(List<ResourceActivityRuleRow> rules) {
        ruleRepository.deleteAllInBatch();
        for (ResourceActivityRuleRow row : rules) {
            var resource = resourceRepository.findById(UUID.fromString(row.resourceId()))
                    .orElseThrow(() -> new IllegalArgumentException("Unknown resource id: " + row.resourceId()));
            var activity = activityRepository.findById(UUID.fromString(row.activityId()))
                    .orElseThrow(() -> new IllegalArgumentException("Unknown activity id: " + row.activityId()));
            ResourceActivityRuleEntity entity = new ResourceActivityRuleEntity(
                    UUID.randomUUID(),
                    resource,
                    activity,
                    row.driverWeight());
            ruleRepository.save(entity);
        }
    }
}
