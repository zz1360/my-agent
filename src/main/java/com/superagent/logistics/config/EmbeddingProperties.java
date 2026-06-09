package com.superagent.logistics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "agent.embedding")
public class EmbeddingProperties {

    private String provider = "transformers";
    private boolean fallbackToHashing;
    private String modelName = "BAAI/bge-small-zh-v1.5";
    private String modelUri = "file:${user.dir}/.local-models/bge-small-zh-v1.5/onnx/model_quantized.onnx";
    private String tokenizerUri = "file:${user.dir}/.local-models/bge-small-zh-v1.5/tokenizer.json";
    private String modelOutputName = "last_hidden_state";
    private String cacheDirectory = "${java.io.tmpdir}/spring-ai-onnx-model";
    private boolean disableCaching = true;
    private String queryInstruction = "为这个句子生成表示以用于检索相关文章：";
    private String passageInstruction = "";
    private Map<String, String> tokenizerOptions = new HashMap<>(Map.of(
            "padding", "true",
            "truncation", "true"
    ));

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isFallbackToHashing() {
        return fallbackToHashing;
    }

    public void setFallbackToHashing(boolean fallbackToHashing) {
        this.fallbackToHashing = fallbackToHashing;
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

    public String getCacheDirectory() {
        return cacheDirectory;
    }

    public void setCacheDirectory(String cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
    }

    public boolean isDisableCaching() {
        return disableCaching;
    }

    public void setDisableCaching(boolean disableCaching) {
        this.disableCaching = disableCaching;
    }

    public String getQueryInstruction() {
        return queryInstruction;
    }

    public void setQueryInstruction(String queryInstruction) {
        this.queryInstruction = queryInstruction;
    }

    public String getPassageInstruction() {
        return passageInstruction;
    }

    public void setPassageInstruction(String passageInstruction) {
        this.passageInstruction = passageInstruction;
    }

    public Map<String, String> getTokenizerOptions() {
        return tokenizerOptions;
    }

    public void setTokenizerOptions(Map<String, String> tokenizerOptions) {
        this.tokenizerOptions = tokenizerOptions == null ? new HashMap<>() : tokenizerOptions;
    }
}
