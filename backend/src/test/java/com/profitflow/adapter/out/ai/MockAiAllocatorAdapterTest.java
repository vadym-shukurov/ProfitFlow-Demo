package com.profitflow.adapter.out.ai;

import com.profitflow.application.model.AiSuggestion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MockAiAllocatorAdapter}.
 *
 * <p>Verifies each keyword branch and the fallback path.
 */
class MockAiAllocatorAdapterTest {

    private final MockAiAllocatorAdapter adapter = new MockAiAllocatorAdapter();

    @ParameterizedTest
    @ValueSource(strings = {"Zendesk subscription", "Spent $5k on zendesk", "ZENDESK renewal"})
    void zendeskKeywordSuggestsCustomerSupport(String text) {
        AiSuggestion suggestion = adapter.suggest(text);
        assertThat(suggestion.suggestedActivityName()).isEqualTo("Customer Support");
        assertThat(suggestion.suggestedAllocationDriver()).isEqualTo("Tickets resolved");
    }

    @ParameterizedTest
    @ValueSource(strings = {"AWS EC2 servers", "Cloud infrastructure", "azure blob storage"})
    void cloudKeywordSuggestsInfrastructure(String text) {
        AiSuggestion suggestion = adapter.suggest(text);
        assertThat(suggestion.suggestedActivityName()).isEqualTo("Infrastructure");
        assertThat(suggestion.suggestedAllocationDriver()).isEqualTo("CPU hours");
    }

    @Test
    void serverKeywordSuggestsInfrastructure() {
        AiSuggestion suggestion = adapter.suggest("Dedicated server hosting");
        assertThat(suggestion.suggestedActivityName()).isEqualTo("Infrastructure");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void blankOrNullInputFallsBackToGeneralAdministration(String text) {
        AiSuggestion suggestion = adapter.suggest(text);
        assertThat(suggestion.suggestedActivityName()).isEqualTo("General Administration");
        assertThat(suggestion.suggestedAllocationDriver()).isEqualTo("Headcount");
    }

    @Test
    void unrecognisedTextFallsBackToGeneralAdministration() {
        AiSuggestion suggestion = adapter.suggest("Random expense without keywords");
        assertThat(suggestion.suggestedActivityName()).isEqualTo("General Administration");
    }
}
