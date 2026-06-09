package com.superagent.logistics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.transformers.TransformersEmbeddingModel;

import static org.assertj.core.api.Assertions.assertThat;

class TransformersEmbeddingModelTests {

    @Test
    @EnabledIfSystemProperty(named = "local.bge.model-uri", matches = ".+")
    void localBgeSmallZhModelProducesSemanticVector() throws Exception {
        TransformersEmbeddingModel model = new TransformersEmbeddingModel();
        model.setTokenizerResource(System.getProperty("local.bge.tokenizer-uri"));
        model.setModelResource(System.getProperty("local.bge.model-uri"));
        model.setDisableCaching(true);
        model.afterPropertiesSet();

        float[] vector = model.embed("冷链运输温度异常怎么处理");

        assertThat(vector).hasSize(512);
        assertThat(hasNonZeroValue(vector)).isTrue();
    }

    private boolean hasNonZeroValue(float[] vector) {
        for (float value : vector) {
            if (Math.abs(value) > 0.000001f) {
                return true;
            }
        }
        return false;
    }
}
