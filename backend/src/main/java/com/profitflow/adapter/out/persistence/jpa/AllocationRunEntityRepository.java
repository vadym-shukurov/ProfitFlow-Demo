package com.profitflow.adapter.out.persistence.jpa;

import com.profitflow.adapter.out.persistence.entity.AllocationRunEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AllocationRunEntityRepository extends JpaRepository<AllocationRunEntity, Long> {

    Page<AllocationRunEntity> findAllByOrderByRunNumberDesc(Pageable pageable);
}
