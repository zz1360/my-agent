package com.superagent.logistics.llm;

import com.superagent.logistics.config.LlmGatewayProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ModelRouter {

    private final LlmGatewayProperties properties;

    public ModelRouter(LlmGatewayProperties properties) {
        this.properties = properties;
    }

    public ModelRouteDecision decide(String routeKey) {
        String resolvedRouteKey = hasText(routeKey) ? routeKey : properties.getDefaultRoute();
        LlmGatewayProperties.Route route = properties.getRoutes().get(resolvedRouteKey);
        if (route == null) {
            route = properties.getRoutes().get(properties.getDefaultRoute());
        }
        if (route == null) {
            route = new LlmGatewayProperties.Route();
        }

        Set<String> modelIds = new LinkedHashSet<>();
        if (hasText(route.getPrimaryModel())) {
            modelIds.add(route.getPrimaryModel());
        }
        if (hasText(route.getFallbackModel())) {
            modelIds.add(route.getFallbackModel());
        }

        List<ModelCandidate> candidates = new ArrayList<>();
        for (String modelId : modelIds) {
            LlmGatewayProperties.Model model = model(modelId);
            candidates.add(new ModelCandidate(
                    modelId,
                    model.getProvider(),
                    model.getModel(),
                    model.isEnabled(),
                    model.isSupportsStreaming(),
                    model.isSupportsToolCalling(),
                    Math.max(1_000L, route.getTimeoutMs()),
                    route.getMaxPromptTokens(),
                    route.getMaxCompletionTokens(),
                    route.getTemperature()
            ));
        }
        return new ModelRouteDecision(resolvedRouteKey, candidates);
    }

    private LlmGatewayProperties.Model model(String modelId) {
        Map<String, LlmGatewayProperties.Model> models = properties.getModels();
        LlmGatewayProperties.Model model = models.get(modelId);
        if (model != null) {
            return model;
        }
        LlmGatewayProperties.Model fallback = new LlmGatewayProperties.Model();
        fallback.setModel(modelId);
        fallback.setProvider(modelId.startsWith("deepseek") ? "deepseek" : "unknown");
        fallback.setEnabled(false);
        return fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
