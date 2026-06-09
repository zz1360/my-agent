package com.superagent.logistics.config;

import com.superagent.logistics.knowledge.KnowledgeReranker;
import com.superagent.logistics.knowledge.LightweightKnowledgeReranker;
import com.superagent.logistics.knowledge.LocalOnnxKnowledgeReranker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

@Configuration
@EnableConfigurationProperties(RerankerProperties.class)
public class RerankerConfig {

    private static final Logger log = LoggerFactory.getLogger(RerankerConfig.class);

    @Bean
    public KnowledgeReranker knowledgeReranker(RerankerProperties properties) {
        String provider = properties.getProvider() == null
                ? "onnx"
                : properties.getProvider().trim().toLowerCase(Locale.ROOT);
        if ("lightweight".equals(provider)) {
            log.info("Using lightweight knowledge reranker for tests/fallback");
            return new LightweightKnowledgeReranker();
        }
        if (!"onnx".equals(provider)) {
            throw new IllegalStateException("Unsupported reranker provider: " + properties.getProvider());
        }
        try {
            KnowledgeReranker reranker = new LocalOnnxKnowledgeReranker(properties);
            log.info("Using local ONNX knowledge reranker: modelName={}, modelUri={}, tokenizerUri={}",
                    properties.getModelName(), properties.getModelUri(), properties.getTokenizerUri());
            return reranker;
        } catch (Exception ex) {
            if (properties.isFallbackToLightweight()) {
                log.warn("Local ONNX reranker is unavailable; falling back to lightweight reranker: {}", ex.getMessage());
                return new LightweightKnowledgeReranker();
            }
            throw new IllegalStateException("Local ONNX reranker is unavailable. Configure local model/tokenizer "
                    + "or set agent.reranker.provider=lightweight for tests only.", ex);
        }
    }
}
