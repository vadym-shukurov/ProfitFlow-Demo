package com.profitflow.application.service;

import com.profitflow.application.exception.InvalidInputException;
import com.profitflow.application.port.out.ProductRepositoryPort;
import com.profitflow.domain.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProductCatalogService}.
 */
@ExtendWith(MockitoExtension.class)
class ProductCatalogServiceTest {

    @Mock
    private ProductRepositoryPort repository;

    private ProductCatalogService service;

    @BeforeEach
    void setUp() {
        service = new ProductCatalogService(repository);
    }

    @Test
    void listProductsDelegatesToRepository() {
        List<Product> expected = List.of(new Product("p-1", "Widget A"));
        when(repository.findAll()).thenReturn(expected);

        assertThat(service.listProducts()).isEqualTo(expected);
    }

    @Test
    void createProductPersistsWithGeneratedId() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createProduct("Widget B");

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Widget B");
        assertThat(captor.getValue().id()).isNotBlank();
    }

    @Test
    void createProductStripsWhitespace() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createProduct("  Enterprise License  ");

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Enterprise License");
    }

    @Test
    void createProductRejectsBlankName() {
        assertThatThrownBy(() -> service.createProduct(""))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void createProductRejectsNullName() {
        assertThatThrownBy(() -> service.createProduct(null))
                .isInstanceOf(InvalidInputException.class);
    }
}
