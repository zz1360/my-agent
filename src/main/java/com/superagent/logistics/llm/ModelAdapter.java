package com.superagent.logistics.llm;

public interface ModelAdapter {

    boolean supports(ModelCandidate candidate);

    LlmResponse chat(LlmRequest request, ModelCandidate candidate);

    LlmResponse stream(LlmRequest request, ModelCandidate candidate, LlmStreamListener listener);
}
