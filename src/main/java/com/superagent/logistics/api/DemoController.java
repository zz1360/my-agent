package com.superagent.logistics.api;

import com.superagent.logistics.knowledge.KnowledgeSearchService;
import com.superagent.logistics.knowledge.PgVectorKnowledgeStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final KnowledgeSearchService knowledgeSearchService;
    private final PgVectorKnowledgeStore vectorKnowledgeStore;

    public DemoController(KnowledgeSearchService knowledgeSearchService,
                          PgVectorKnowledgeStore vectorKnowledgeStore) {
        this.knowledgeSearchService = knowledgeSearchService;
        this.vectorKnowledgeStore = vectorKnowledgeStore;
    }

    @GetMapping("/questions")
    public Map<String, List<String>> questions() {
        return Map.of("questions", knowledgeSearchService.demoQuestions());
    }

    @GetMapping("/vector-store/status")
    public Map<String, Object> vectorStoreStatus() {
        return Map.of(
                "provider", "pgvector",
                "enabled", vectorKnowledgeStore.isEnabled(),
                "ready", vectorKnowledgeStore.isReady(),
                "table", vectorKnowledgeStore.table(),
                "chunks", vectorKnowledgeStore.countChunks()
        );
    }
}
