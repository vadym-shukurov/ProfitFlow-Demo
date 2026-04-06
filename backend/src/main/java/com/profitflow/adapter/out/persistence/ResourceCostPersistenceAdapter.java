package com.profitflow.adapter.out.persistence;

import com.profitflow.adapter.out.persistence.jpa.ResourceCostEntityRepository;
import com.profitflow.application.port.out.ResourceCostRepositoryPort;
import com.profitflow.domain.ResourceCost;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ResourceCostPersistenceAdapter implements ResourceCostRepositoryPort {

    private final ResourceCostEntityRepository repository;

    public ResourceCostPersistenceAdapter(ResourceCostEntityRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ResourceCost> findAll() {
        return repository.findAll().stream().map(PersistenceMapper::toResourceCost).toList();
    }

    @Override
    public ResourceCost save(ResourceCost resourceCost) {
        return PersistenceMapper.toResourceCost(
                repository.save(PersistenceMapper.toResourceCostEntity(resourceCost)));
    }
}
