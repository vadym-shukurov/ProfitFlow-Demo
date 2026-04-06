package com.profitflow.application.port.in;

import com.profitflow.application.model.AiSuggestion;

/**
 * Inbound port for AI-assisted cost allocation suggestions.
 *
 * <p>Accepts a free-text expense description and returns a suggested activity
 * category and allocation driver from the AI allocator adapter.
 */
public interface AiSuggestionUseCase {

    /**
     * Returns an AI-generated allocation suggestion for the given expense description.
     *
     * @param naturalLanguageExpense free-text description of the spend
     * @return suggested activity name and allocation driver; never {@code null}
     */
    AiSuggestion suggest(String naturalLanguageExpense);
}
