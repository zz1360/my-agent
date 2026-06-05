package com.superagent.logistics.knowledge;

import com.superagent.logistics.config.PgVectorProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class LocalTextEmbeddingService {

    private final int dimension;

    public LocalTextEmbeddingService(PgVectorProperties properties) {
        this.dimension = properties.getDimension();
    }

    public float[] embed(String text) {
        float[] vector = new float[dimension];
        for (String feature : features(text)) {
            int hash = feature.hashCode();
            int index = Math.floorMod(hash, dimension);
            float sign = (hash & 1) == 0 ? 1.0f : -1.0f;
            vector[index] += sign * weight(feature);
        }
        normalize(vector);
        return vector;
    }

    private List<String> features(String text) {
        String normalized = expandDomainTerms(text == null ? "" : text)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
        List<String> result = new ArrayList<>();
        if (normalized.isBlank()) {
            return result;
        }
        for (String token : normalized.split("[^a-z0-9\\u4e00-\\u9fa5]+")) {
            if (token.length() >= 2) {
                result.add("tok:" + token);
            }
        }
        String compact = normalized.replaceAll("[^a-z0-9\\u4e00-\\u9fa5]", "");
        for (int i = 0; i < compact.length(); i++) {
            result.add("c1:" + compact.charAt(i));
            if (i + 1 < compact.length()) {
                result.add("c2:" + compact.substring(i, i + 2));
            }
            if (i + 2 < compact.length()) {
                result.add("c3:" + compact.substring(i, i + 3));
            }
        }
        return result;
    }

    private String expandDomainTerms(String text) {
        String expanded = text;
        if (text.contains("晚到") || text.contains("晚了") || text.contains("迟到") || text.contains("超时")) {
            expanded += " 延误 时效 超承诺 赔付 补偿 SLA";
        }
        if (text.contains("赔") || text.contains("补偿")) {
            expanded += " 赔付 补偿 合同 SLA 规则";
        }
        if (text.contains("冷链") || text.contains("超温") || text.contains("温度")) {
            expanded += " 冷链 温控 超温 货损 质检";
        }
        if (text.contains("投诉") || text.contains("客诉") || text.contains("工单")) {
            expanded += " 投诉 工单 客服 升级 SOP";
        }
        return expanded;
    }

    private float weight(String feature) {
        if (feature.startsWith("tok:")) {
            return 2.0f;
        }
        if (feature.startsWith("c3:")) {
            return 1.4f;
        }
        if (feature.startsWith("c2:")) {
            return 1.2f;
        }
        return 0.6f;
    }

    private void normalize(float[] vector) {
        double norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm == 0) {
            return;
        }
        float scale = (float) (1.0 / Math.sqrt(norm));
        for (int i = 0; i < vector.length; i++) {
            vector[i] *= scale;
        }
    }
}
