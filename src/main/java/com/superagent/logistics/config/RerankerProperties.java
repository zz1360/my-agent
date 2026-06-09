package com.superagent.logistics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "agent.reranker")
public class RerankerProperties {

    private String provider = "onnx";
    private boolean fallbackToLightweight = true;
    private String modelName = "Xenova/bge-reranker-base";
    private String modelUri = "file:${user.dir}/.local-models/bge-reranker-base/onnx/model_quantized.onnx";
    private String tokenizerUri = "file:${user.dir}/.local-models/bge-reranker-base/tokenizer.json";
    private String modelOutputName = "logits";
    private int maxCandidates = 24;
    private double modelWeight = 0.72;
    private double ruleWeight = 0.28;
    private Map<String, String> tokenizerOptions = new HashMap<>(Map.of(
            "padding", "true",
            "truncation", "true",
            "maxLength", "512"
    ));

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isFallbackToLightweight() {
        return fallbackToLightweight;
    }

    public void setFallbackToLightweight(boolean fallbackToLightweight) {
        this.fallbackToLightweight = fallbackToLightweight;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getModelUri() {
        return modelUri;
    }

    public void setModelUri(String modelUri) {
        this.modelUri = modelUri;
    }

    public String getTokenizerUri() {
        return tokenizerUri;
    }

    public void setTokenizerUri(String tokenizerUri) {
        this.tokenizerUri = tokenizerUri;
    }

    public String getModelOutputName() {
        return modelOutputName;
    }

    public void setModelOutputName(String modelOutputName) {
        this.modelOutputName = modelOutputName;
    }

    public int getMaxCandidates() {
        return maxCandidates;
    }

    public void setMaxCandidates(int maxCandidates) {
        this.maxCandidates = maxCandidates;
    }

    public double getModelWeight() {
        return modelWeight;
    }

    public void setModelWeight(double modelWeight) {
        this.modelWeight = modelWeight;
    }

    public double getRuleWeight() {
        return ruleWeight;
    }

    public void setRuleWeight(double ruleWeight) {
        this.ruleWeight = ruleWeight;
    }

    public Map<String, String> getTokenizerOptions() {
        return tokenizerOptions;
    }

    public void setTokenizerOptions(Map<String, String> tokenizerOptions) {
        this.tokenizerOptions = tokenizerOptions == null ? new HashMap<>() : tokenizerOptions;
    }
}
