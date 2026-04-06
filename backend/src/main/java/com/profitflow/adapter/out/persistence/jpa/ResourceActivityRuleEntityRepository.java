package com.profitflow.adapter.out.persistence.jpa;

import com.profitflow.adapter.out.persistence.entity.ResourceActivityRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ResourceActivityRuleEntityRepository extends JpaRepository<ResourceActivityRuleEntity, UUID> {

    @Query("select r from ResourceActivityRuleEntity r join fetch r.resource join fetch r.activity")
    List<ResourceActivityRuleEntity> findAllWithAssociations();
}
