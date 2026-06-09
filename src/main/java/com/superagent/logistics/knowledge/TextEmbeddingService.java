package com.superagent.logistics.knowledge;

public interface TextEmbeddingService {

    float[] embedQuery(String text);

    float[] embedDocument(String text);

    String providerName();

    boolean semantic();
}
