package com.superagent.logistics;

import com.superagent.logistics.config.RerankerProperties;
import com.superagent.logistics.knowledge.KnowledgeChunk;
import com.superagent.logistics.knowledge.KnowledgeRerankCandidate;
import com.superagent.logistics.knowledge.KnowledgeSearchResult;
import com.superagent.logistics.knowledge.LocalOnnxKnowledgeReranker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalOnnxKnowledgeRerankerTests {

    @Test
    @EnabledIfSystemProperty(named = "local.reranker.model-uri", matches = ".+")
    void localBgeRerankerRanksRelevantChunkHigher() throws Exception {
        RerankerProperties properties = new RerankerProperties();
        properties.setModelName(System.getProperty("local.reranker.model-name", "local-test-reranker"));
        properties.setModelUri(System.getProperty("local.reranker.model-uri"));
        properties.setTokenizerUri(System.getProperty("local.reranker.tokenizer-uri"));
        properties.setMaxCandidates(2);
        LocalOnnxKnowledgeReranker reranker = new LocalOnnxKnowledgeReranker(properties);

        KnowledgeChunk relevant = new KnowledgeChunk(
                "policy-cold-chain-v2",
                "policy-cold-chain-v2-chunk-001",
                "Cold chain temperature exception SOP",
                "Cold chain shipments must stay between 2 and 8 degrees Celsius. A temperature breach above 10 degrees must create an exception ticket.",
                "",
                "CUSTOMER_SERVICE"
        );
        KnowledgeChunk unrelated = new KnowledgeChunk(
                "faq-tracking",
                "faq-tracking-chunk-001",
                "Tracking status FAQ",
                "Tracking status includes collected, in warehouse, in transit, out for delivery and signed.",
                "",
                "CUSTOMER_SERVICE"
        );

        List<KnowledgeSearchResult> results = reranker.rerank("How should customer service handle a cold chain temperature breach above 10 degrees?", List.of(
                new KnowledgeRerankCandidate(unrelated, 0.2, 0.2, 0.2),
                new KnowledgeRerankCandidate(relevant, 0.2, 0.2, 0.2)
        ), 2);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).chunk().docId()).isEqualTo("policy-cold-chain-v2");
        assertThat(results.get(0).rerankerScore()).isNotNull();
        assertThat(results.get(0).rerankerProvider()).startsWith("onnx:");
    }
}
