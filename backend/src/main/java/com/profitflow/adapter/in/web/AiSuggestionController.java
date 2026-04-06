package com.profitflow.adapter.in.web;

import com.profitflow.adapter.in.web.dto.AiSuggestRequest;
import com.profitflow.application.model.AiSuggestion;
import com.profitflow.application.port.in.AiSuggestionUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiSuggestionController {

    private final AiSuggestionUseCase aiSuggestions;

    public AiSuggestionController(AiSuggestionUseCase aiSuggestions) {
        this.aiSuggestions = aiSuggestions;
    }

    @PostMapping("/suggest")
    public AiSuggestion suggest(@Valid @RequestBody AiSuggestRequest request) {
        return aiSuggestions.suggest(request.text());
    }
}
