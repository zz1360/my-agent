package com.superagent.logistics.api;

import com.superagent.logistics.agent.LogisticsAgentService;
import com.superagent.logistics.api.dto.AgentChatRequest;
import com.superagent.logistics.api.dto.AgentChatResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentChatController {

    private final LogisticsAgentService agentService;

    public AgentChatController(LogisticsAgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/chat")
    public AgentChatResponse chat(@Valid @RequestBody AgentChatRequest request) {
        return agentService.chat(request);
    }
}
