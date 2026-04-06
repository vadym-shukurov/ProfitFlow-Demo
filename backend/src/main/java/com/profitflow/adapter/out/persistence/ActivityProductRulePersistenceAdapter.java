package com.profitflow.adapter.out.persistence;

import com.profitflow.adapter.out.persistence.entity.ActivityProductRuleEntity;
import com.profitflow.adapter.out.persistence.jpa.ActivityEntityRepository;
import com.profitflow.adapter.out.persistence.jpa.ActivityProductRuleEntityRepository;
import com.profitflow.adapter.out.persistence.jpa.ProductEntityRepository;
import com.profitflow.application.model.ActivityProductRuleRow;
import com.profitflow.application.port.out.ActivityProductRuleRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class ActivityProductRulePersistenceAdapter implements ActivityProductRuleRepositoryPort {

    private final ActivityProductRuleEntityRepository ruleRepository;
    private final ActivityEntityRepository activityRepository;
    private final ProductEntityRepository productRepository;

    public ActivityProductRulePersistenceAdapter(
            ActivityProductRuleEntityRepository ruleRepository,
            ActivityEntityRepository activityRepository,
            ProductEntityRepository productRepository) {
        this.ruleRepository = ruleRepository;
        this.activityRepository = activityRepository;
        this.productRepository = productRepository;
    }

    @Override
    public List<ActivityProductRuleRow> findAllRows() {
        return ruleRepository.findAllWithAssociations().stream()
                .map(r -> new ActivityProductRuleRow(
                        r.getActivity().getId().toString(),
                        r.getProduct().getId().toString(),
                        r.getDriverWeight()))
                .toList();
    }

    @Override
    @Transactional
    public void replaceAll(List<ActivityProductRuleRow> rules) {
        ruleRepository.deleteAllInBatch();
        for (ActivityProductRuleRow row : rules) {
            var activity = activityRepository.findById(UUID.fromString(row.activityId()))
                    .orElseThrow(() -> new IllegalArgumentException("Unknown activity id: " + row.activityId()));
            var product = productRepository.findById(UUID.fromString(row.productId()))
                    .orElseThrow(() -> new IllegalArgumentException("Unknown product id: " + row.productId()));
            ActivityProductRuleEntity entity = new ActivityProductRuleEntity(
                    UUID.randomUUID(),
                    activity,
                    product,
                    row.driverWeight());
            ruleRepository.save(entity);
        }
    }
}
