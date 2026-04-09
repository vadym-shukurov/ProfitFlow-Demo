package com.profitflow.application.service;

import com.profitflow.application.exception.InvalidInputException;
import com.profitflow.application.exception.ResourceConflictException;
import com.profitflow.application.exception.ResourceNotFoundException;
import com.profitflow.application.port.in.ResourceCostUseCase;
import com.profitflow.application.port.out.ResourceCostRepositoryPort;
import com.profitflow.domain.Money;
import com.profitflow.domain.ResourceCost;
import com.profitflow.application.port.out.BusinessMetricsPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ResourceCostService}.
 *
 * <p>All database I/O is replaced by a Mockito stub so these tests run without
 * a Spring context or a database.
 */
@ExtendWith(MockitoExtension.class)
class ResourceCostServiceTest {

    @Mock
    private ResourceCostRepositoryPort repository;
    @Mock
    private BusinessMetricsPort metrics;

    private ResourceCostService service;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ResourceCostUseCase> selfProvider = mock(ObjectProvider.class);
        service = new ResourceCostService(repository, metrics, selfProvider);
        lenient().when(selfProvider.getObject()).thenReturn(service);
    }

    // -------------------------------------------------------------------------
    // listCosts
    // -------------------------------------------------------------------------

    @Test
    void listCostsDelegatesToRepository() {
        List<ResourceCost> expected = List.of(
                new ResourceCost("id-1", "Servers", Money.usd(new BigDecimal("1000.00"))));
        when(repository.findAll()).thenReturn(expected);

        assertThat(service.listCosts()).isEqualTo(expected);
    }

    // -------------------------------------------------------------------------
    // createCost
    // -------------------------------------------------------------------------

    @Test
    void createCostPersistsWithGeneratedUuid() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResourceCost created = service.createCost("IT Servers", new BigDecimal("5000"), "USD");

        ArgumentCaptor<ResourceCost> captor = ArgumentCaptor.forClass(ResourceCost.class);
        verify(repository).save(captor.capture());
        ResourceCost saved = captor.getValue();

        assertThat(saved.label()).isEqualTo("IT Servers");
        assertThat(saved.amount().amount()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(saved.id()).isNotBlank();
    }

    @Test
    void createCostDefaultsToUsdWhenCurrencyNull() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResourceCost created = service.createCost("Rent", new BigDecimal("1000"), null);

        assertThat(created.amount().currency().getCurrencyCode()).isEqualTo("USD");
    }

    @Test
    void createCostDefaultsToUsdWhenCurrencyBlank() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResourceCost created = service.createCost("Rent", new BigDecimal("1000"), "  ");

        assertThat(created.amount().currency().getCurrencyCode()).isEqualTo("USD");
    }

    @Test
    void createCostRejectsBlankLabel() {
        assertThatThrownBy(() -> service.createCost("  ", BigDecimal.ONE, "USD"))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("label");
    }

    @Test
    void createCostRejectsNullLabel() {
        assertThatThrownBy(() -> service.createCost(null, BigDecimal.ONE, "USD"))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void createCostRejectsUnknownCurrencyCode() {
        assertThatThrownBy(() -> service.createCost("X", BigDecimal.ONE, "INVALID"))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("INVALID");
    }

    // -------------------------------------------------------------------------
    // importCostsFromCsv
    // -------------------------------------------------------------------------

    @Test
    void importCsvWithHeaderRowSkipsHeader() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String csv = "label,amount\nZendesk,5000\nAWS,10000";
        List<ResourceCost> imported = service.importCostsFromCsv(csv);

        assertThat(imported).hasSize(2);
        verify(repository, times(2)).save(any());
    }

    @Test
    void importCsvWithoutHeaderRowParsesAllLines() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String csv = "Zendesk,5000\nAWS,10000,USD";
        List<ResourceCost> imported = service.importCostsFromCsv(csv);

        assertThat(imported).hasSize(2);
        assertThat(imported.get(0).label()).isEqualTo("Zendesk");
        assertThat(imported.get(1).amount().amount()).isEqualByComparingTo(new BigDecimal("10000.00"));
    }

    @Test
    void importCsvWithCustomCurrencyColumn() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String csv = "London Office,8000,GBP";
        List<ResourceCost> imported = service.importCostsFromCsv(csv);

        assertThat(imported.getFirst().amount().currency().getCurrencyCode()).isEqualTo("GBP");
    }

    @Test
    void importCsvBlankInputReturnsEmptyList() {
        assertThat(service.importCostsFromCsv("   ")).isEmpty();
        assertThat(service.importCostsFromCsv(null)).isEmpty();
    }

    @Test
    void importCsvRowWithFewerThanTwoColumnsThrows() {
        assertThatThrownBy(() -> service.importCostsFromCsv("OnlyOneColumn"))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("2 columns");
    }

    @Test
    void importCsvNonNumericAmountThrows() {
        assertThatThrownBy(() -> service.importCostsFromCsv("Rent,not-a-number"))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("non-numeric");
    }

    @Test
    void importCsvRecordsFailureMetricOnException() {
        assertThatThrownBy(() -> service.importCostsFromCsv("Bad,row,with,extra"))
                .isInstanceOf(Exception.class);
        verify(metrics).recordCostImportFailure();
    }

    @Test
    void createCostRecordsMetric() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.createCost("Office", new BigDecimal("500"), "USD");
        verify(metrics).recordCostCreated();
    }

    // -------------------------------------------------------------------------
    // deleteCost
    // -------------------------------------------------------------------------

    @Test
    void deleteCostRejectsBlankId() {
        assertThatThrownBy(() -> service.deleteCost("  "))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void deleteCostThrowsNotFoundWhenMissing() {
        when(repository.existsById("missing")).thenReturn(false);
        assertThatThrownBy(() -> service.deleteCost("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteCostDelegatesToRepositoryWhenExists() {
        when(repository.existsById("id-1")).thenReturn(true);
        service.deleteCost("id-1");
        verify(repository).deleteById("id-1");
    }

    @Test
    void deleteCostMapsIntegrityViolationToConflict() {
        when(repository.existsById("id-1")).thenReturn(true);
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("fk"))
                .when(repository).deleteById("id-1");

        assertThatThrownBy(() -> service.deleteCost("id-1"))
                .isInstanceOf(ResourceConflictException.class);
    }

    // -------------------------------------------------------------------------
    // stripCsvInjectionChars
    // -------------------------------------------------------------------------

    @Test
    void stripCsvInjectionCharsRemovesLeadingEquals() {
        assertThat(ResourceCostService.stripCsvInjectionChars("=BAD")).isEqualTo("BAD");
    }

    @Test
    void stripCsvInjectionCharsRemovesLeadingPlus() {
        assertThat(ResourceCostService.stripCsvInjectionChars("+BAD")).isEqualTo("BAD");
    }

    @Test
    void stripCsvInjectionCharsRemovesLeadingAt() {
        assertThat(ResourceCostService.stripCsvInjectionChars("@formula")).isEqualTo("formula");
    }

    @Test
    void stripCsvInjectionCharsLeavesNormalLabelUntouched() {
        assertThat(ResourceCostService.stripCsvInjectionChars("Servers")).isEqualTo("Servers");
    }

    @Test
    void stripCsvInjectionCharsHandlesNull() {
        assertThat(ResourceCostService.stripCsvInjectionChars(null)).isNull();
    }

    @Test
    void stripCsvInjectionCharsRemovesMultipleLeadingChars() {
        assertThat(ResourceCostService.stripCsvInjectionChars("==cmd")).isEqualTo("cmd");
    }
}
