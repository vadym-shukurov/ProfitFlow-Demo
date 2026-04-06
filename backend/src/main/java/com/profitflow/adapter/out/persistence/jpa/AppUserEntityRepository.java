package com.profitflow.adapter.out.persistence.jpa;

import com.profitflow.adapter.out.persistence.entity.AppUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link AppUserEntity}. */
public interface AppUserEntityRepository extends JpaRepository<AppUserEntity, UUID> {

    Optional<AppUserEntity> findByUsername(String username);
}
