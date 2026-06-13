package com.superagent.logistics.api;

import com.superagent.logistics.api.dto.AgentChatRequest;
import com.superagent.logistics.api.dto.AgentChatResponse;
import com.superagent.logistics.api.dto.AgentConversationDetail;
import com.superagent.logistics.api.dto.AgentConversationSummary;
import com.superagent.logistics.api.dto.AgentMessageFeedbackRequest;
import com.superagent.logistics.api.dto.AgentMessageFeedbackResponse;
import com.superagent.logistics.conversation.AgentConversationService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/agent")
public class AgentChatController {

    private final AgentConversationService conversationService;

    public AgentChatController(AgentConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/chat")
    public AgentChatResponse chat(@Valid @RequestBody AgentChatRequest request) {
        return conversationService.chat(request);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody AgentChatRequest request) {
        return conversationService.chatStream(request);
    }

    @GetMapping("/conversations")
    public List<AgentConversationSummary> conversations(@RequestParam(required = false) String tenantId,
                                                        @RequestParam(required = false) String userId,
                                                        @RequestParam(required = false) List<String> roles,
                                                        @RequestParam(defaultValue = "20") int limit) {
        return conversationService.listConversations(tenantId, userId, roles, limit);
    }

    @GetMapping("/conversations/{conversationId}")
    public AgentConversationDetail conversation(@PathVariable String conversationId,
                                                @RequestParam(required = false) String tenantId,
                                                @RequestParam(required = false) String userId,
                                                @RequestParam(required = false) List<String> roles) {
        return conversationService.getConversation(conversationId, tenantId, userId, roles);
    }

    @PostMapping("/messages/{messageId}/feedback")
    public AgentMessageFeedbackResponse feedback(@PathVariable String messageId,
                                                 @Valid @RequestBody AgentMessageFeedbackRequest request) {
        return conversationService.feedback(messageId, request);
    }
}
