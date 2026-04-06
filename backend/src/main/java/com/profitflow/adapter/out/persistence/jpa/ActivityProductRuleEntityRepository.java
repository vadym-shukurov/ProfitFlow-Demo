package com.profitflow.adapter.out.persistence.jpa;

import com.profitflow.adapter.out.persistence.entity.ActivityProductRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ActivityProductRuleEntityRepository extends JpaRepository<ActivityProductRuleEntity, UUID> {

    @Query("select r from ActivityProductRuleEntity r join fetch r.activity join fetch r.product")
    List<ActivityProductRuleEntity> findAllWithAssociations();
}
