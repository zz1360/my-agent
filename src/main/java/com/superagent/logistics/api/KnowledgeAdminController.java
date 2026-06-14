package com.superagent.logistics.api;

import com.superagent.logistics.api.dto.Citation;
import com.superagent.logistics.api.dto.KnowledgeIndexJobResponse;
import com.superagent.logistics.api.dto.KnowledgeDocumentRequest;
import com.superagent.logistics.api.dto.KnowledgeDocumentResponse;
import com.superagent.logistics.api.dto.KnowledgePreviewResponse;
import com.superagent.logistics.api.dto.KnowledgeReindexResponse;
import com.superagent.logistics.api.dto.KnowledgeSearchPreviewResponse;
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

    @PostMapping("/documents/preview")
    public KnowledgePreviewResponse preview(@Valid @RequestBody KnowledgeDocumentRequest request) {
        return knowledgeAdminService.preview(request);
    }

    @GetMapping("/documents")
    public List<KnowledgeDocumentResponse> list(@RequestParam(required = false) String tenantId,
                                                @RequestParam(required = false) String userId,
                                                @RequestParam(required = false) List<String> roles,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) String bizDomain,
                                                @RequestParam(required = false) String baseDocId,
                                                @RequestParam(defaultValue = "50") int limit) {
        return knowledgeAdminService.list(tenantId, userId, roles, status, bizDomain, baseDocId, limit);
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

    @PostMapping("/documents/{docId}/publish")
    public KnowledgeDocumentResponse publish(@PathVariable String docId,
                                             @RequestParam(required = false) String tenantId,
                                             @RequestParam(required = false) String userId,
                                             @RequestParam(required = false) List<String> roles) {
        return knowledgeAdminService.publish(tenantId, docId, userId, roles);
    }

    @PostMapping("/documents/{docId}/expire")
    public KnowledgeDocumentResponse expire(@PathVariable String docId,
                                            @RequestParam(required = false) String tenantId,
                                            @RequestParam(required = false) String userId,
                                            @RequestParam(required = false) List<String> roles) {
        return knowledgeAdminService.expire(tenantId, docId, userId, roles);
    }

    @PostMapping("/reindex")
    public KnowledgeReindexResponse reindex(@RequestParam(required = false) String tenantId,
                                            @RequestParam(required = false) String userId,
                                            @RequestParam(required = false) List<String> roles) {
        return knowledgeAdminService.reindex(tenantId, userId, roles);
    }

    @GetMapping("/index-jobs")
    public List<KnowledgeIndexJobResponse> listIndexJobs(@RequestParam(required = false) String tenantId,
                                                         @RequestParam(required = false) String userId,
                                                         @RequestParam(required = false) List<String> roles,
                                                         @RequestParam(defaultValue = "20") int limit) {
        return knowledgeAdminService.listIndexJobs(tenantId, userId, roles, limit);
    }

    @GetMapping("/index-jobs/{jobId}")
    public KnowledgeIndexJobResponse getIndexJob(@PathVariable String jobId,
                                                 @RequestParam(required = false) String tenantId,
                                                 @RequestParam(required = false) String userId,
                                                 @RequestParam(required = false) List<String> roles) {
        return knowledgeAdminService.getIndexJob(tenantId, userId, roles, jobId);
    }

    @GetMapping("/search")
    public List<Citation> search(@RequestParam(required = false) String tenantId,
                                 @RequestParam(required = false) String userId,
                                 @RequestParam(required = false) List<String> roles,
                                 @RequestParam String query,
                                 @RequestParam(defaultValue = "5") int topK,
                                 @RequestParam(defaultValue = "hybrid_reranker") String mode) {
        return knowledgeAdminService.search(tenantId, userId, roles, query, topK, mode);
    }

    @GetMapping("/search/preview")
    public KnowledgeSearchPreviewResponse searchPreview(@RequestParam(required = false) String tenantId,
                                                        @RequestParam(required = false) String userId,
                                                        @RequestParam(required = false) List<String> roles,
                                                        @RequestParam String query,
                                                        @RequestParam(defaultValue = "5") int topK,
                                                        @RequestParam(defaultValue = "hybrid_reranker") String mode) {
        return knowledgeAdminService.searchPreview(tenantId, userId, roles, query, topK, mode);
    }
}
