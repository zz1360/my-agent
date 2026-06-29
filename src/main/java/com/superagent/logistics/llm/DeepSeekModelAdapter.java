package com.superagent.logistics.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class DeepSeekModelAdapter implements ModelAdapter {

    private final ObjectProvider<ChatClient> chatClientProvider;
    private final LlmUsageEstimator usageEstimator;

    public DeepSeekModelAdapter(@Qualifier("deepSeekChatClient") ObjectProvider<ChatClient> chatClientProvider,
                                LlmUsageEstimator usageEstimator) {
        this.chatClientProvider = chatClientProvider;
        this.usageEstimator = usageEstimator;
    }

    @Override
    public boolean supports(ModelCandidate candidate) {
        return "deepseek".equalsIgnoreCase(candidate.provider())
                && chatClientProvider.getIfAvailable() != null;
    }

    @Override
    public LlmResponse chat(LlmRequest request, ModelCandidate candidate) {
        long started = System.nanoTime();
        ChatResponse chatResponse = requestSpec(request, candidate)
                .call()
                .chatResponse();
        long latencyMs = (System.nanoTime() - started) / 1_000_000;
        String content = content(chatResponse);
        LlmUsage usage = usage(chatResponse, request.prompt(), content);
        return new LlmResponse(candidate.provider(), candidate.model(), request.routeKey(), content,
                usage, latencyMs, latencyMs, false);
    }

    @Override
    public LlmResponse stream(LlmRequest request, ModelCandidate candidate, LlmStreamListener listener) {
        long started = System.nanoTime();
        AtomicLong firstTokenNanos = new AtomicLong(0);
        AtomicReference<LlmUsage> usage = new AtomicReference<>(LlmUsage.empty());
        StringBuilder answer = new StringBuilder();

        requestSpec(request, candidate)
                .stream()
                .chatResponse()
                .doOnNext(response -> {
                    LlmUsage responseUsage = usage(response, request.prompt(), answer.toString());
                    if (responseUsage.totalTokens() != null && responseUsage.totalTokens() > 0
                            && !responseUsage.estimated()) {
                        usage.set(responseUsage);
                        listener.onUsage(responseUsage);
                    }
                    String delta = content(response);
                    if (delta == null || delta.isEmpty()) {
                        return;
                    }
                    if (firstTokenNanos.compareAndSet(0, System.nanoTime())) {
                        long ttftMs = (firstTokenNanos.get() - started) / 1_000_000;
                        listener.onStatus("模型已开始返回，TTFT " + ttftMs + " ms");
                    }
                    answer.append(delta);
                    listener.onDelta(delta);
                })
                .blockLast(Duration.ofMillis(Math.max(1_000L, candidate.timeoutMs())));

        long latencyMs = (System.nanoTime() - started) / 1_000_000;
        LlmUsage finalUsage = usage.get();
        if (finalUsage.totalTokens() == null || finalUsage.totalTokens() <= 0) {
            finalUsage = usageEstimator.estimate(request.prompt(), answer.toString());
            listener.onUsage(finalUsage);
        }
        Long ttftMs = firstTokenNanos.get() == 0 ? null : (firstTokenNanos.get() - started) / 1_000_000;
        return new LlmResponse(candidate.provider(), candidate.model(), request.routeKey(), answer.toString(),
                finalUsage, latencyMs, ttftMs, true);
    }

    private ChatClientRequestSpec requestSpec(LlmRequest request, ModelCandidate candidate) {
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null) {
            throw new IllegalStateException("DeepSeek ChatClient is unavailable");
        }
        ChatClientRequestSpec spec = chatClient.prompt()
                .options(OpenAiChatOptions.builder()
                        .model(candidate.model())
                        .temperature(candidate.temperature())
                        .maxTokens(candidate.maxCompletionTokens())
                        .streamUsage(true)
                        .internalToolExecutionEnabled(true)
                        .build())
                .user(request.prompt());
        if (request.tools().length > 0) {
            spec = spec.tools(request.tools());
        }
        if (!request.toolContext().isEmpty()) {
            spec = spec.toolContext(request.toolContext());
        }
        return spec;
    }

    private String content(ChatResponse response) {
        if (response == null) {
            return "";
        }
        Generation result = response.getResult();
        if (result == null || result.getOutput() == null || result.getOutput().getText() == null) {
            return "";
        }
        return result.getOutput().getText();
    }

    private LlmUsage usage(ChatResponse response, String prompt, String completion) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return usageEstimator.estimate(prompt, completion);
        }
        Usage usage = response.getMetadata().getUsage();
        Integer promptTokens = usage.getPromptTokens();
        Integer completionTokens = usage.getCompletionTokens();
        Integer totalTokens = usage.getTotalTokens();
        if (promptTokens == null && completionTokens == null && totalTokens == null) {
            return usageEstimator.estimate(prompt, completion);
        }
        return new LlmUsage(promptTokens, completionTokens, totalTokens, false);
    }
}
