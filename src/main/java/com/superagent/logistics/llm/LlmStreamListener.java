package com.superagent.logistics.llm;

public interface LlmStreamListener {

    default void onStatus(String message) {
    }

    default void onDelta(String delta) {
    }

    default void onUsage(LlmUsage usage) {
    }
}
