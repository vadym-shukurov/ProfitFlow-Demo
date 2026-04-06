package com.profitflow.adapter.out.persistence;

import com.profitflow.adapter.out.persistence.jpa.ProductEntityRepository;
import com.profitflow.application.port.out.ProductRepositoryPort;
import com.profitflow.domain.Product;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ProductPersistenceAdapter implements ProductRepositoryPort {

    private final ProductEntityRepository repository;

    public ProductPersistenceAdapter(ProductEntityRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Product> findAll() {
        return repository.findAll().stream().map(PersistenceMapper::toProduct).toList();
    }

    @Override
    public Optional<Product> findById(String id) {
        return repository.findById(UUID.fromString(id)).map(PersistenceMapper::toProduct);
    }

    @Override
    public Product save(Product product) {
        return PersistenceMapper.toProduct(
                repository.save(PersistenceMapper.toProductEntity(product)));
    }
}
