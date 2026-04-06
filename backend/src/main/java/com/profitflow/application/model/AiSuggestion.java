package com.profitflow.application.model;

/**
 * Immutable result from the AI allocator containing a suggested activity category
 * and the recommended allocation driver for a given expense description.
 *
 * @param suggestedActivityName      the activity (e.g. "Machine Operation") the AI maps the expense to
 * @param suggestedAllocationDriver  the cost driver the AI recommends (e.g. "Machine Hours")
 */
public record AiSuggestion(String suggestedActivityName, String suggestedAllocationDriver) {
}
