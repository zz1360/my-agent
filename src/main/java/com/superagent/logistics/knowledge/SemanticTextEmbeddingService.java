package com.superagent.logistics.knowledge;

import com.superagent.logistics.config.EmbeddingProperties;
import org.springframework.ai.embedding.EmbeddingModel;

public class SemanticTextEmbeddingService implements TextEmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingProperties properties;

    public SemanticTextEmbeddingService(EmbeddingModel embeddingModel, EmbeddingProperties properties) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    @Override
    public float[] embedQuery(String text) {
        return embeddingModel.embed(withInstruction(properties.getQueryInstruction(), text));
    }

    @Override
    public float[] embedDocument(String text) {
        return embeddingModel.embed(withInstruction(properties.getPassageInstruction(), text));
    }

    @Override
    public String providerName() {
        return "transformers:" + properties.getModelName();
    }

    @Override
    public boolean semantic() {
        return true;
    }

    private String withInstruction(String instruction, String text) {
        String normalized = text == null ? "" : text.trim();
        if (instruction == null || instruction.isBlank()) {
            return normalized;
        }
        return instruction.trim() + normalized;
    }
}
