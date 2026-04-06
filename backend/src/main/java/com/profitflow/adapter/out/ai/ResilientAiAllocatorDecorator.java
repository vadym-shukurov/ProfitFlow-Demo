package com.profitflow.adapter.out.ai;

import com.profitflow.application.model.AiSuggestion;
import com.profitflow.application.port.out.AiAllocatorPort;
import com.profitflow.application.port.out.BusinessMetricsPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Wraps the concrete {@link AiAllocatorPort} with a wall-clock timeout and metrics.
 *
 * <p>On timeout or unexpected failure, returns the same safe fallback as the mock
 * adapter ({@code General Administration} / {@code Headcount}) so callers still get a 200.
 */
@Component
@Primary
public class ResilientAiAllocatorDecorator implements AiAllocatorPort {

    private static final AiSuggestion FALLBACK =
            new AiSuggestion("General Administration", "Headcount");

    private final AiAllocatorPort       delegate;
    private final long                  timeoutMs;
    private final BusinessMetricsPort   metrics;
    private final ExecutorService       executor = Executors.newVirtualThreadPerTaskExecutor();

    public ResilientAiAllocatorDecorator(
            @Qualifier("mockAiAllocatorAdapter") AiAllocatorPort delegate,
            @Value("${profitflow.ai.suggest-timeout-ms:3000}") long timeoutMs,
            BusinessMetricsPort metrics) {
        this.delegate   = delegate;
        this.timeoutMs  = timeoutMs;
        this.metrics    = metrics;
    }

    @PreDestroy
    void shutdownExecutor() {
        executor.shutdown();
    }

    @Override
    public AiSuggestion suggest(String naturalLanguageExpense) {
        long start = System.nanoTime();
        Future<AiSuggestion> future = executor.submit(
                () -> delegate.suggest(naturalLanguageExpense));
        try {
            AiSuggestion s = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            metrics.recordAiSuggestionSuccess(System.nanoTime() - start);
            return s;
        } catch (TimeoutException e) {
            future.cancel(true);
            metrics.recordAiSuggestionTimeout();
            return FALLBACK;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            metrics.recordAiSuggestionError();
            return FALLBACK;
        } catch (ExecutionException e) {
            metrics.recordAiSuggestionError();
            return FALLBACK;
        }
    }
}
