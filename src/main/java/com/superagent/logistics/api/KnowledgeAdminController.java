package com.superagent.logistics.api;

import com.superagent.logistics.api.dto.Citation;
import com.superagent.logistics.api.dto.KnowledgeDocumentRequest;
import com.superagent.logistics.api.dto.KnowledgeDocumentResponse;
import com.superagent.logistics.api.dto.KnowledgeReindexResponse;
import com.superagent.logistics.knowledge.KnowledgeAdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeAdminController {

    private final KnowledgeAdminService knowledgeAdminService;

    public KnowledgeAdminController(KnowledgeAdminService knowledgeAdminService) {
        this.knowledgeAdminService = knowledgeAdminService;
    }

    @PostMapping("/documents")
    public KnowledgeDocumentResponse upsert(@Valid @RequestBody KnowledgeDocumentRequest request) {
        return knowledgeAdminService.upsert(request);
    }

    @GetMapping("/documents")
    public List<KnowledgeDocumentResponse> list(@RequestParam(required = false) String tenantId,
                                                @RequestParam(required = false) String userId,
                                                @RequestParam(required = false) List<String> roles,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) String bizDomain,
                                                @RequestParam(defaultValue = "50") int limit) {
        return knowledgeAdminService.list(tenantId, userId, roles, status, bizDomain, limit);
    }

    @GetMapping("/documents/{docId}")
    public KnowledgeDocumentResponse get(@PathVariable String docId,
                                         @RequestParam(required = false) String tenantId,
                                         @RequestParam(required = false) String userId,
                                         @RequestParam(required = false) List<String> roles) {
        return knowledgeAdminService.get(tenantId, docId, userId, roles);
    }

    @PostMapping("/documents/{docId}/disable")
    public KnowledgeDocumentResponse disable(@PathVariable String docId,
                                             @RequestParam(required = false) String tenantId,
                                             @RequestParam(required = false) String userId,
                                             @RequestParam(required = false) List<String> roles) {
        return knowledgeAdminService.disable(tenantId, docId, userId, roles);
    }

    @PostMapping("/reindex")
    public KnowledgeReindexResponse reindex(@RequestParam(required = false) String tenantId,
                                            @RequestParam(required = false) String userId,
                                            @RequestParam(required = false) List<String> roles) {
        return knowledgeAdminService.reindex(tenantId, userId, roles);
    }

    @GetMapping("/search")
    public List<Citation> search(@RequestParam(required = false) String tenantId,
                                 @RequestParam(required = false) String userId,
                                 @RequestParam(required = false) List<String> roles,
                                 @RequestParam String query,
                                 @RequestParam(defaultValue = "5") int topK) {
        return knowledgeAdminService.search(tenantId, userId, roles, query, topK);
    }
}
