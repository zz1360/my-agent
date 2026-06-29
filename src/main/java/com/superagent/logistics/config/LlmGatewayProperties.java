package com.superagent.logistics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "agent.llm")
public class LlmGatewayProperties {

    private String defaultRoute = "logistics-chat";
    private Map<String, Route> routes = new LinkedHashMap<>();
    private Map<String, Model> models = new LinkedHashMap<>();

    public LlmGatewayProperties() {
        Route chatRoute = new Route();
        chatRoute.setPrimaryModel("deepseek-v4-flash");
        chatRoute.setFallbackModel("local-rule");
        chatRoute.setTimeoutMs(30_000L);
        chatRoute.setMaxPromptTokens(6_000);
        chatRoute.setMaxCompletionTokens(1_800);
        chatRoute.setTemperature(0.2);
        routes.put("logistics-chat", chatRoute);

        Route diagnosisRoute = new Route();
        diagnosisRoute.setPrimaryModel("deepseek-v4-flash");
        diagnosisRoute.setFallbackModel("local-rule");
        diagnosisRoute.setTimeoutMs(45_000L);
        diagnosisRoute.setMaxPromptTokens(8_000);
        diagnosisRoute.setMaxCompletionTokens(2_400);
        diagnosisRoute.setTemperature(0.2);
        routes.put("customer-diagnosis", diagnosisRoute);

        Model deepSeek = new Model();
        deepSeek.setProvider("deepseek");
        deepSeek.setModel("deepseek-v4-flash");
        deepSeek.setEnabled(true);
        deepSeek.setSupportsStreaming(true);
        deepSeek.setSupportsToolCalling(true);
        models.put("deepseek-v4-flash", deepSeek);

        Model localRule = new Model();
        localRule.setProvider("local-rule");
        localRule.setModel("local-rule");
        localRule.setEnabled(true);
        localRule.setSupportsStreaming(false);
        localRule.setSupportsToolCalling(false);
        models.put("local-rule", localRule);
    }

    public String getDefaultRoute() {
        return defaultRoute;
    }

    public void setDefaultRoute(String defaultRoute) {
        this.defaultRoute = defaultRoute;
    }

    public Map<String, Route> getRoutes() {
        return routes;
    }

    public void setRoutes(Map<String, Route> routes) {
        this.routes = routes == null ? new LinkedHashMap<>() : routes;
    }

    public Map<String, Model> getModels() {
        return models;
    }

    public void setModels(Map<String, Model> models) {
        this.models = models == null ? new LinkedHashMap<>() : models;
    }

    public static class Route {

        private String primaryModel = "deepseek-v4-flash";
        private String fallbackModel = "local-rule";
        private long timeoutMs = 30_000L;
        private Integer maxPromptTokens = 6_000;
        private Integer maxCompletionTokens = 1_800;
        private Double temperature = 0.2;

        public String getPrimaryModel() {
            return primaryModel;
        }

        public void setPrimaryModel(String primaryModel) {
            this.primaryModel = primaryModel;
        }

        public String getFallbackModel() {
            return fallbackModel;
        }

        public void setFallbackModel(String fallbackModel) {
            this.fallbackModel = fallbackModel;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public Integer getMaxPromptTokens() {
            return maxPromptTokens;
        }

        public void setMaxPromptTokens(Integer maxPromptTokens) {
            this.maxPromptTokens = maxPromptTokens;
        }

        public Integer getMaxCompletionTokens() {
            return maxCompletionTokens;
        }

        public void setMaxCompletionTokens(Integer maxCompletionTokens) {
            this.maxCompletionTokens = maxCompletionTokens;
        }

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }
    }

    public static class Model {

        private String provider = "deepseek";
        private String model = "deepseek-v4-flash";
        private boolean enabled = true;
        private boolean supportsStreaming = true;
        private boolean supportsToolCalling = true;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isSupportsStreaming() {
            return supportsStreaming;
        }

        public void setSupportsStreaming(boolean supportsStreaming) {
            this.supportsStreaming = supportsStreaming;
        }

        public boolean isSupportsToolCalling() {
            return supportsToolCalling;
        }

        public void setSupportsToolCalling(boolean supportsToolCalling) {
            this.supportsToolCalling = supportsToolCalling;
        }
    }
}
