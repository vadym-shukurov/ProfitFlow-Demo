package com.profitflow.adapter.out.persistence.jpa;

import com.profitflow.adapter.out.persistence.entity.ActivityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ActivityEntityRepository extends JpaRepository<ActivityEntity, UUID> {
}
