package com.superagent.logistics.llm;

import java.util.Optional;

public interface LlmGateway {

    Optional<LlmResponse> chat(LlmRequest request);

    Optional<LlmResponse> stream(LlmRequest request, LlmStreamListener listener);
}
