package com.profitflow.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for AI-powered allocation suggestions.
 *
 * <p>The 500-character limit prevents prompt-injection attempts from embedding
 * very long instruction sequences into the cost-label field.
 */
public record AiSuggestRequest(
        @NotBlank @Size(max = 500) String text) {
}
