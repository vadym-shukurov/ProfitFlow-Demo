package com.profitflow.application.service;

import com.profitflow.application.exception.InvalidInputException;
import com.profitflow.application.port.out.ActivityRepositoryPort;
import com.profitflow.domain.Activity;
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
 * Unit tests for {@link ActivityCatalogService}.
 */
@ExtendWith(MockitoExtension.class)
class ActivityCatalogServiceTest {

    @Mock
    private ActivityRepositoryPort repository;

    private ActivityCatalogService service;

    @BeforeEach
    void setUp() {
        service = new ActivityCatalogService(repository);
    }

    @Test
    void listActivitiesDelegatesToRepository() {
        List<Activity> expected = List.of(new Activity("id-1", "Customer Service"));
        when(repository.findAll()).thenReturn(expected);

        assertThat(service.listActivities()).isEqualTo(expected);
    }

    @Test
    void createActivityPersistsWithGeneratedId() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createActivity("IT Support");

        ArgumentCaptor<Activity> captor = ArgumentCaptor.forClass(Activity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("IT Support");
        assertThat(captor.getValue().id()).isNotBlank();
    }

    @Test
    void createActivityStripsWhitespace() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createActivity("  Logistics  ");

        ArgumentCaptor<Activity> captor = ArgumentCaptor.forClass(Activity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Logistics");
    }

    @Test
    void createActivityRejectsBlankName() {
        assertThatThrownBy(() -> service.createActivity("  "))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void createActivityRejectsNullName() {
        assertThatThrownBy(() -> service.createActivity(null))
                .isInstanceOf(InvalidInputException.class);
    }
}
