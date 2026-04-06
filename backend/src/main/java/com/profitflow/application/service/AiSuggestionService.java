package com.profitflow.application.service;

import com.profitflow.application.exception.InvalidInputException;
import com.profitflow.application.model.AiSuggestion;
import com.profitflow.application.port.in.AiSuggestionUseCase;
import com.profitflow.application.port.out.AiAllocatorPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Application service delegating to {@link AiAllocatorPort}.
 *
 * <p><strong>Data governance</strong>: free-text may contain PII; production adapters must
 * apply retention limits, redaction, and provider DPAs. User input is capped to mitigate
 * prompt-injection surface and unbounded token cost.
 */
@Service
public class AiSuggestionService implements AiSuggestionUseCase {

    private final AiAllocatorPort aiAllocator;
    private final int             maxInputChars;

    public AiSuggestionService(
            AiAllocatorPort aiAllocator,
            @Value("${profitflow.ai.max-input-chars:4000}") int maxInputChars) {
        this.aiAllocator   = aiAllocator;
        this.maxInputChars = maxInputChars;
    }

    @Override
    public AiSuggestion suggest(String naturalLanguageExpense) {
        String raw = Objects.requireNonNullElse(naturalLanguageExpense, "");
        if (raw.length() > maxInputChars) {
            throw new InvalidInputException(
                    "Expense description exceeds maximum length of " + maxInputChars + " characters");
        }
        return aiAllocator.suggest(raw);
    }
}
