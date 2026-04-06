package com.profitflow.application.port.out;

import com.profitflow.application.model.AiSuggestion;

/**
 * Outbound port for an external allocator (LLM or rules engine).
 *
 * <h2>Production expectations</h2>
 * <ul>
 *   <li><strong>Timeouts &amp; budgets</strong> — bounded wall-clock and token spend per request.</li>
 *   <li><strong>PII &amp; retention</strong> — minimise text sent to third parties; contractually bound
 *       retention; no training on customer data unless explicitly agreed.</li>
 *   <li><strong>Prompt injection</strong> — treat input as untrusted; system prompts hardened;
 *       output validated before use.</li>
 *   <li><strong>Resilience</strong> — circuit breaker / fallback to deterministic heuristic when
 *       the provider is unavailable.</li>
 * </ul>
 */
public interface AiAllocatorPort {

    AiSuggestion suggest(String naturalLanguageExpense);
}
