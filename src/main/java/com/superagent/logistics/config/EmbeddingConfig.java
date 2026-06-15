package com.superagent.logistics.config;

import com.superagent.logistics.knowledge.HashingTextEmbeddingService;
import com.superagent.logistics.knowledge.SemanticTextEmbeddingService;
import com.superagent.logistics.knowledge.TextEmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

@Configuration
@EnableConfigurationProperties({EmbeddingProperties.class, PgVectorProperties.class, RetrievalProperties.class})
public class EmbeddingConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingConfig.class);

    @Bean
    public TextEmbeddingService textEmbeddingService(EmbeddingProperties embeddingProperties,
                                                     PgVectorProperties vectorProperties) {
        String provider = embeddingProperties.getProvider() == null
                ? "transformers"
                : embeddingProperties.getProvider().trim().toLowerCase(Locale.ROOT);
        if ("hashing".equals(provider)) {
            log.info("Using hashing embedding provider for tests/fallback; semantic embedding is disabled");
            return new HashingTextEmbeddingService(vectorProperties.getDimension());
        }
        if (!"transformers".equals(provider)) {
            throw new IllegalStateException("Unsupported embedding provider: " + embeddingProperties.getProvider());
        }
        try {
            TransformersEmbeddingModel model = new TransformersEmbeddingModel();
            if (hasText(embeddingProperties.getTokenizerUri())) {
                model.setTokenizerResource(embeddingProperties.getTokenizerUri().trim());
            }
            if (hasText(embeddingProperties.getModelUri())) {
                model.setModelResource(embeddingProperties.getModelUri().trim());
            }
            if (hasText(embeddingProperties.getModelOutputName())) {
                model.setModelOutputName(embeddingProperties.getModelOutputName().trim());
            }
            if (hasText(embeddingProperties.getCacheDirectory())) {
                model.setResourceCacheDirectory(resolveSystemProperty(embeddingProperties.getCacheDirectory().trim()));
            }
            model.setDisableCaching(embeddingProperties.isDisableCaching());
            if (embeddingProperties.getTokenizerOptions() != null && !embeddingProperties.getTokenizerOptions().isEmpty()) {
                model.setTokenizerOptions(embeddingProperties.getTokenizerOptions());
            }
            model.afterPropertiesSet();
            log.info("Using local Transformers embedding provider: modelName={}, modelUri={}, tokenizerUri={}",
                    embeddingProperties.getModelName(), blankLabel(embeddingProperties.getModelUri()),
                    blankLabel(embeddingProperties.getTokenizerUri()));
            return new SemanticTextEmbeddingService(model, embeddingProperties);
        } catch (Exception ex) {
            if (embeddingProperties.isFallbackToHashing()) {
                log.warn("Local Transformers embedding is unavailable; falling back to hashing provider: {}", ex.getMessage());
                return new HashingTextEmbeddingService(vectorProperties.getDimension());
            }
            throw new IllegalStateException("Local Transformers embedding is unavailable. Configure local ONNX model/tokenizer "
                    + "or set agent.embedding.provider=hashing for tests only.", ex);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String blankLabel(String value) {
        return hasText(value) ? value : "<spring-ai-default>";
    }

    private String resolveSystemProperty(String value) {
        if (value.contains("${java.io.tmpdir}")) {
            return value.replace("${java.io.tmpdir}", System.getProperty("java.io.tmpdir"));
        }
        if (value.contains("${user.home}")) {
            return value.replace("${user.home}", System.getProperty("user.home"));
        }
        if (value.contains("${user.dir}")) {
            return value.replace("${user.dir}", System.getProperty("user.dir"));
        }
        return value;
    }
}
