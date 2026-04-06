package com.profitflow.adapter.out.persistence;

import com.profitflow.adapter.out.persistence.entity.ActivityEntity;
import com.profitflow.adapter.out.persistence.entity.ProductEntity;
import com.profitflow.adapter.out.persistence.entity.ResourceCostEntity;
import com.profitflow.domain.Activity;
import com.profitflow.domain.Money;
import com.profitflow.domain.Product;
import com.profitflow.domain.ResourceCost;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistenceMapperTest {

    @Test
    void toResourceCostMapsEntityToDomain() {
        UUID id = UUID.randomUUID();
        ResourceCostEntity entity = new ResourceCostEntity(id, "AWS", new BigDecimal("123.4500"), "USD");

        ResourceCost domain = PersistenceMapper.toResourceCost(entity);

        assertThat(domain.id()).isEqualTo(id.toString());
        assertThat(domain.label()).isEqualTo("AWS");
        assertThat(domain.amount().amount()).isEqualByComparingTo(new BigDecimal("123.45"));
        assertThat(domain.amount().currency()).isEqualTo(Currency.getInstance("USD"));
    }

    @Test
    void toResourceCostRejectsInvalidCurrencyCode() {
        ResourceCostEntity entity = new ResourceCostEntity(UUID.randomUUID(), "Bad", BigDecimal.ONE, "USDX");

        assertThatThrownBy(() -> PersistenceMapper.toResourceCost(entity))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toResourceCostEntityMapsDomainToEntity() {
        UUID id = UUID.randomUUID();
        ResourceCost domain = new ResourceCost(id.toString(), "GCP", new Money(new BigDecimal("10.00"), Currency.getInstance("EUR")));

        ResourceCostEntity entity = PersistenceMapper.toResourceCostEntity(domain);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getLabel()).isEqualTo("GCP");
        assertThat(entity.getAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(entity.getCurrencyCode()).isEqualTo("EUR");
    }

    @Test
    void activityMappingsRoundTripIdAndName() {
        UUID id = UUID.randomUUID();
        ActivityEntity entity = new ActivityEntity(id, "Engineering");

        Activity domain = PersistenceMapper.toActivity(entity);
        assertThat(domain.id()).isEqualTo(id.toString());
        assertThat(domain.name()).isEqualTo("Engineering");

        ActivityEntity mappedBack = PersistenceMapper.toActivityEntity(domain);
        assertThat(mappedBack.getId()).isEqualTo(id);
        assertThat(mappedBack.getName()).isEqualTo("Engineering");
    }

    @Test
    void productMappingsRoundTripIdAndName() {
        UUID id = UUID.randomUUID();
        ProductEntity entity = new ProductEntity(id, "ProfitFlow");

        Product domain = PersistenceMapper.toProduct(entity);
        assertThat(domain.id()).isEqualTo(id.toString());
        assertThat(domain.name()).isEqualTo("ProfitFlow");

        ProductEntity mappedBack = PersistenceMapper.toProductEntity(domain);
        assertThat(mappedBack.getId()).isEqualTo(id);
        assertThat(mappedBack.getName()).isEqualTo("ProfitFlow");
    }
}

