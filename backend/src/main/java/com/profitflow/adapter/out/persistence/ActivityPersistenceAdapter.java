package com.profitflow.adapter.out.persistence;

import com.profitflow.adapter.out.persistence.jpa.ActivityEntityRepository;
import com.profitflow.application.port.out.ActivityRepositoryPort;
import com.profitflow.domain.Activity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ActivityPersistenceAdapter implements ActivityRepositoryPort {

    private final ActivityEntityRepository repository;

    public ActivityPersistenceAdapter(ActivityEntityRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Activity> findAll() {
        return repository.findAll().stream().map(PersistenceMapper::toActivity).toList();
    }

    @Override
    public Optional<Activity> findById(String id) {
        return repository.findById(UUID.fromString(id)).map(PersistenceMapper::toActivity);
    }

    @Override
    public Activity save(Activity activity) {
        return PersistenceMapper.toActivity(
                repository.save(PersistenceMapper.toActivityEntity(activity)));
    }
}
