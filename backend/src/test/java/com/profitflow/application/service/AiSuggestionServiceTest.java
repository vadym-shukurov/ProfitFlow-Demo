package com.profitflow.application.service;

import com.profitflow.application.exception.InvalidInputException;
import com.profitflow.application.model.AiSuggestion;
import com.profitflow.application.port.out.AiAllocatorPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AiSuggestionService}.
 */
@ExtendWith(MockitoExtension.class)
class AiSuggestionServiceTest {

    @Mock
    private AiAllocatorPort aiAllocatorPort;

    private AiSuggestionService service;

    @BeforeEach
    void setUp() {
        service = new AiSuggestionService(aiAllocatorPort, 4000);
    }

    @Test
    void suggestDelegatesToAdapter() {
        AiSuggestion expected = new AiSuggestion("Machine Operation", "Machine Hours");
        when(aiAllocatorPort.suggest(anyString())).thenReturn(expected);

        AiSuggestion result = service.suggest("factory electricity bill");

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void suggestPassesInputToAdapter() {
        when(aiAllocatorPort.suggest(anyString()))
                .thenReturn(new AiSuggestion("IT", "Headcount"));

        service.suggest("software license renewal");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(aiAllocatorPort).suggest(captor.capture());
        assertThat(captor.getValue()).isEqualTo("software license renewal");
    }

    @Test
    void suggestConvertsNullInputToEmptyString() {
        when(aiAllocatorPort.suggest(anyString()))
                .thenReturn(new AiSuggestion("General", "Headcount"));

        service.suggest(null);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(aiAllocatorPort).suggest(captor.capture());
        assertThat(captor.getValue()).isEqualTo("");
    }

    @Test
    void suggestRejectsInputExceedingMaxLength() {
        String tooLong = "x".repeat(4001);
        assertThatThrownBy(() -> service.suggest(tooLong))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("maximum length");
    }

    @Test
    void suggestReturnsAdapterResult() {
        AiSuggestion expected = new AiSuggestion("Customer Support", "Support Tickets");
        when(aiAllocatorPort.suggest(anyString())).thenReturn(expected);

        AiSuggestion result = service.suggest("call centre staffing costs");

        assertThat(result.suggestedActivityName()).isEqualTo("Customer Support");
        assertThat(result.suggestedAllocationDriver()).isEqualTo("Support Tickets");
    }
}
