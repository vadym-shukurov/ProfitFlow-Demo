package com.profitflow.adapter.out.ai;

import com.profitflow.application.model.AiSuggestion;
import com.profitflow.application.port.out.AiAllocatorPort;
import com.profitflow.application.port.out.BusinessMetricsPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResilientAiAllocatorDecoratorTest {

    @Mock
    private AiAllocatorPort delegate;
    @Mock
    private BusinessMetricsPort metrics;

    @Test
    void delegatesWhenDelegateReturnsQuickly() {
        when(delegate.suggest(anyString())).thenReturn(new AiSuggestion("A", "B"));
        ResilientAiAllocatorDecorator cut =
                new ResilientAiAllocatorDecorator(delegate, 5_000, metrics);

        assertThat(cut.suggest("zendesk").suggestedActivityName()).isEqualTo("A");

        verify(metrics).recordAiSuggestionSuccess(anyLong());
    }

    @Test
    @Timeout(10)
    void returnsFallbackOnTimeout() {
        when(delegate.suggest(anyString())).thenAnswer(inv -> {
            Thread.sleep(500);
            return new AiSuggestion("Late", "X");
        });
        ResilientAiAllocatorDecorator cut =
                new ResilientAiAllocatorDecorator(delegate, 50, metrics);

        AiSuggestion s = cut.suggest("anything");
        assertThat(s.suggestedActivityName()).isEqualTo("General Administration");
        assertThat(s.suggestedAllocationDriver()).isEqualTo("Headcount");

        verify(metrics).recordAiSuggestionTimeout();
    }

    @Test
    void delegateRuntimeExceptionReturnsFallback() {
        when(delegate.suggest(anyString())).thenThrow(new RuntimeException("provider down"));
        ResilientAiAllocatorDecorator cut =
                new ResilientAiAllocatorDecorator(delegate, 5_000, metrics);

        AiSuggestion s = cut.suggest("x");
        assertThat(s.suggestedActivityName()).isEqualTo("General Administration");
        verify(metrics).recordAiSuggestionError();
    }
}
