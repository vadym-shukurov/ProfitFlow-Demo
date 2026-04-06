package com.profitflow.adapter.out.persistence.jpa;

import com.profitflow.adapter.out.persistence.entity.ResourceCostEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ResourceCostEntityRepository extends JpaRepository<ResourceCostEntity, UUID> {
}
