package com.superagent.logistics.knowledge;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.util.PairList;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.superagent.logistics.config.RerankerProperties;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class LocalOnnxKnowledgeReranker implements KnowledgeReranker {

    private final RerankerProperties properties;
    private final HuggingFaceTokenizer tokenizer;
    private final OrtEnvironment environment;
    private final OrtSession session;
    private final Set<String> inputNames;
    private final String outputName;

    public LocalOnnxKnowledgeReranker(RerankerProperties properties) throws Exception {
        this.properties = properties;
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        Resource tokenizerResource = resourceLoader.getResource(resolveSystemProperty(properties.getTokenizerUri()));
        Resource modelResource = resourceLoader.getResource(resolveSystemProperty(properties.getModelUri()));
        this.tokenizer = HuggingFaceTokenizer.newInstance(tokenizerResource.getInputStream(), properties.getTokenizerOptions());
        this.environment = OrtEnvironment.getEnvironment();
        this.session = environment.createSession(modelResource.getContentAsByteArray(), new OrtSession.SessionOptions());
        this.inputNames = session.getInputNames();
        Set<String> outputNames = session.getOutputNames();
        this.outputName = hasText(properties.getModelOutputName()) && outputNames.contains(properties.getModelOutputName())
                ? properties.getModelOutputName()
                : outputNames.stream().findFirst().orElseThrow(() -> new IllegalStateException("Reranker ONNX model has no outputs"));
    }

    @Override
    public List<KnowledgeSearchResult> rerank(String query, List<KnowledgeRerankCandidate> candidates, int topK) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        int modelLimit = Math.min(Math.max(1, properties.getMaxCandidates()), candidates.size());
        List<KnowledgeRerankCandidate> limitedCandidates = candidates.stream()
                .sorted(Comparator.comparingDouble(KnowledgeRerankCandidate::ruleScore).reversed())
                .limit(modelLimit)
                .toList();
        double[] modelScores = score(query, limitedCandidates);
        List<KnowledgeSearchResult> results = new ArrayList<>();
        for (int i = 0; i < limitedCandidates.size(); i++) {
            KnowledgeRerankCandidate candidate = limitedCandidates.get(i);
            double rerankerScore = sigmoid(modelScores[i]);
            double finalScore = properties.getModelWeight() * rerankerScore + properties.getRuleWeight() * candidate.ruleScore();
            results.add(new KnowledgeSearchResult(candidate.chunk(), finalScore, candidate.vectorScore(),
                    candidate.keywordScore(), candidate.ruleScore(), rerankerScore, providerName()));
        }
        return results.stream()
                .sorted(Comparator.comparingDouble(KnowledgeSearchResult::score).reversed())
                .limit(Math.max(1, topK))
                .toList();
    }

    @Override
    public String providerName() {
        return "onnx:" + properties.getModelName();
    }

    private double[] score(String query, List<KnowledgeRerankCandidate> candidates) {
        PairList<String, String> pairs = new PairList<>(candidates.size());
        for (KnowledgeRerankCandidate candidate : candidates) {
            pairs.add(safeText(query), passage(candidate.chunk()));
        }
        Encoding[] encodings = tokenizer.batchEncode(pairs, true, true);
        Map<String, OnnxTensor> tensors = new LinkedHashMap<>();
        try {
            long[][] inputIds = toMatrix(encodings, Encoding::getIds);
            long[][] attentionMask = toMatrix(encodings, Encoding::getAttentionMask);
            tensors.put("input_ids", OnnxTensor.createTensor(environment, inputIds));
            tensors.put("attention_mask", OnnxTensor.createTensor(environment, attentionMask));
            if (inputNames.contains("token_type_ids")) {
                tensors.put("token_type_ids", OnnxTensor.createTensor(environment, toMatrix(encodings, Encoding::getTypeIds)));
            }
            tensors.entrySet().removeIf(entry -> !inputNames.contains(entry.getKey()));
            try (OrtSession.Result result = session.run(tensors)) {
                OnnxValue output = result.get(outputName)
                        .orElseGet(() -> result.iterator().next().getValue());
                return extractScores(output.getValue(), candidates.size());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Local ONNX reranker inference failed: " + ex.getMessage(), ex);
        } finally {
            tensors.values().forEach(OnnxTensor::close);
        }
    }

    private long[][] toMatrix(Encoding[] encodings, LongArrayExtractor extractor) {
        long[][] matrix = new long[encodings.length][];
        for (int i = 0; i < encodings.length; i++) {
            matrix[i] = extractor.extract(encodings[i]);
        }
        return matrix;
    }

    private double[] extractScores(Object value, int expectedSize) {
        double[] scores = new double[expectedSize];
        if (value instanceof float[][] logits) {
            for (int i = 0; i < expectedSize; i++) {
                scores[i] = logits[i][0];
            }
            return scores;
        }
        if (value instanceof float[] logits) {
            for (int i = 0; i < expectedSize; i++) {
                scores[i] = logits[i];
            }
            return scores;
        }
        if (value instanceof double[][] logits) {
            for (int i = 0; i < expectedSize; i++) {
                scores[i] = logits[i][0];
            }
            return scores;
        }
        if (value instanceof double[] logits) {
            System.arraycopy(logits, 0, scores, 0, Math.min(expectedSize, logits.length));
            return scores;
        }
        if (value instanceof float[][][] logits) {
            for (int i = 0; i < expectedSize; i++) {
                scores[i] = logits[i][0][0];
            }
            return scores;
        }
        throw new IllegalStateException("Unsupported reranker output type: " + value.getClass().getName());
    }

    private String passage(KnowledgeChunk chunk) {
        return chunk.title() + "\n" + chunk.content();
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }

    private double sigmoid(double value) {
        if (value >= 0) {
            double z = Math.exp(-value);
            return 1.0 / (1.0 + z);
        }
        double z = Math.exp(value);
        return z / (1.0 + z);
    }

    private String resolveSystemProperty(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("${java.io.tmpdir}", System.getProperty("java.io.tmpdir"))
                .replace("${user.home}", System.getProperty("user.home"))
                .replace("${user.dir}", System.getProperty("user.dir"));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @FunctionalInterface
    private interface LongArrayExtractor {
        long[] extract(Encoding encoding);
    }
}
