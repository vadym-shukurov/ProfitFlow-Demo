package com.profitflow.adapter.out.ai;

import com.profitflow.application.model.AiSuggestion;
import com.profitflow.application.port.out.AiAllocatorPort;
import org.springframework.stereotype.Component;

/**
 * Keyword-rule mock that simulates an LLM-based expense categoriser.
 *
 * <p>In production, replace this bean with an implementation that calls the
 * OpenAI Chat Completions API (or equivalent) while conforming to the same
 * {@link AiAllocatorPort} contract. No domain or application code changes are
 * required — only this adapter is swapped.
 *
 * <h2>Rule priority (first match wins)</h2>
 * <ol>
 *   <li><strong>Customer support</strong> — Zendesk, Freshdesk, Intercom, Crisp</li>
 *   <li><strong>Infrastructure</strong> — cloud providers (AWS, Azure, GCP) and
 *       generic terms (server, cloud, hosting)</li>
 *   <li><strong>General Administration</strong> — fallback for everything else or blank input</li>
 * </ol>
 */
@Component("mockAiAllocatorAdapter")
public class MockAiAllocatorAdapter implements AiAllocatorPort {

    @Override
    public AiSuggestion suggest(String naturalLanguageExpense) {
        if (naturalLanguageExpense == null || naturalLanguageExpense.isBlank()) {
            return generalAdmin();
        }
        String lower = naturalLanguageExpense.toLowerCase();

        if (matchesCustomerSupport(lower)) {
            return new AiSuggestion("Customer Support", "Tickets resolved");
        }
        if (matchesInfrastructure(lower)) {
            return new AiSuggestion("Infrastructure", "CPU hours");
        }
        return generalAdmin();
    }

    // -------------------------------------------------------------------------
    // Private keyword matchers
    // -------------------------------------------------------------------------

    private static boolean matchesCustomerSupport(String text) {
        return text.contains("zendesk")
                || text.contains("freshdesk")
                || text.contains("intercom")
                || text.contains("crisp");
    }

    private static boolean matchesInfrastructure(String text) {
        return text.contains("server")
                || text.contains("aws")
                || text.contains("azure")
                || text.contains("gcp")
                || text.contains("cloud")
                || text.contains("hosting")
                || text.contains("kubernetes")
                || text.contains("docker");
    }

    private static AiSuggestion generalAdmin() {
        return new AiSuggestion("General Administration", "Headcount");
    }
}
