package com.superagent.logistics.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DefaultLlmGateway implements LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(DefaultLlmGateway.class);

    private final ModelRouter modelRouter;
    private final List<ModelAdapter> adapters;
    private final ModelCallLogService modelCallLogService;

    public DefaultLlmGateway(ModelRouter modelRouter,
                             List<ModelAdapter> adapters,
                             ModelCallLogService modelCallLogService) {
        this.modelRouter = modelRouter;
        this.adapters = adapters;
        this.modelCallLogService = modelCallLogService;
    }

    @Override
    public Optional<LlmResponse> chat(LlmRequest request) {
        return invoke(request, false, LlmStreamListenerNoop.INSTANCE);
    }

    @Override
    public Optional<LlmResponse> stream(LlmRequest request, LlmStreamListener listener) {
        return invoke(request, true, listener == null ? LlmStreamListenerNoop.INSTANCE : listener);
    }

    private Optional<LlmResponse> invoke(LlmRequest request, boolean streaming, LlmStreamListener listener) {
        ModelRouteDecision decision = modelRouter.decide(request.routeKey());
        String fallbackFrom = null;
        String fallbackReason = null;
        for (ModelCandidate candidate : decision.candidates()) {
            if (!candidate.enabled()) {
                fallbackFrom = appendFallback(fallbackFrom, candidate.modelId());
                fallbackReason = "MODEL_DISABLED";
                continue;
            }
            if (streaming && !candidate.supportsStreaming()) {
                fallbackFrom = appendFallback(fallbackFrom, candidate.modelId());
                fallbackReason = "STREAM_UNSUPPORTED";
                continue;
            }
            Optional<ModelAdapter> adapter = adapters.stream()
                    .filter(item -> item.supports(candidate))
                    .findFirst();
            if (adapter.isEmpty()) {
                fallbackFrom = appendFallback(fallbackFrom, candidate.modelId());
                fallbackReason = "ADAPTER_UNAVAILABLE";
                continue;
            }
            long started = System.nanoTime();
            try {
                listener.onStatus("正在调用模型：" + candidate.provider() + "/" + candidate.model());
                LlmResponse response = streaming
                        ? adapter.get().stream(request, candidate, listener)
                        : adapter.get().chat(request, candidate);
                record(request, decision.routeKey(), candidate, response, "SUCCESS", null, null, fallbackFrom, fallbackReason);
                return Optional.of(response);
            } catch (RuntimeException ex) {
                long latencyMs = (System.nanoTime() - started) / 1_000_000;
                log.warn("LLM model call failed: route={}, provider={}, model={}, error={}",
                        decision.routeKey(), candidate.provider(), candidate.model(), ex.getMessage());
                recordFailure(request, decision.routeKey(), candidate, streaming, latencyMs, ex,
                        fallbackFrom, fallbackReason);
                fallbackFrom = appendFallback(fallbackFrom, candidate.modelId());
                fallbackReason = ex.getClass().getSimpleName();
            }
        }
        return Optional.empty();
    }

    private void record(LlmRequest request,
                        String routeKey,
                        ModelCandidate candidate,
                        LlmResponse response,
                        String status,
                        String errorCode,
                        String errorMessage,
                        String fallbackFrom,
                        String fallbackReason) {
        modelCallLogService.record(new ModelCallLogEntry(
                request.traceId(),
                request.tenantId(),
                request.userId(),
                request.conversationId(),
                request.scene(),
                routeKey,
                candidate.provider(),
                candidate.model(),
                response.streaming(),
                response.usage(),
                response.latencyMs(),
                response.ttftMs(),
                status,
                errorCode,
                errorMessage,
                fallbackFrom,
                fallbackReason
        ));
    }

    private void recordFailure(LlmRequest request,
                               String routeKey,
                               ModelCandidate candidate,
                               boolean streaming,
                               long latencyMs,
                               RuntimeException ex,
                               String fallbackFrom,
                               String fallbackReason) {
        modelCallLogService.record(new ModelCallLogEntry(
                request.traceId(),
                request.tenantId(),
                request.userId(),
                request.conversationId(),
                request.scene(),
                routeKey,
                candidate.provider(),
                candidate.model(),
                streaming,
                LlmUsage.empty(),
                latencyMs,
                null,
                "FAILED",
                "MODEL_ERROR",
                ex.getMessage(),
                fallbackFrom,
                fallbackReason
        ));
    }

    private String appendFallback(String current, String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return current;
        }
        return current == null || current.isBlank() ? modelId : current + "," + modelId;
    }

    private enum LlmStreamListenerNoop implements LlmStreamListener {
        INSTANCE
    }
}
