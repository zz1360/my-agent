package com.superagent.logistics.action;

import com.superagent.logistics.api.dto.AgentActionExecuteRequest;
import com.superagent.logistics.api.dto.AgentActionResponse;
import com.superagent.logistics.security.AgentUserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TicketNoteActionExecutor implements AgentActionExecutor {

    private final JdbcTemplate jdbcTemplate;

    public TicketNoteActionExecutor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String actionType() {
        return "TICKET_NOTE";
    }

    @Override
    public String executorName() {
        return "ticket-note-draft-executor";
    }

    @Override
    public String targetSystem() {
        return "BUSINESS_TICKET_NOTE_TABLE";
    }

    @Override
    public boolean lowRisk() {
        return true;
    }

    @Override
    public ActionExecutionResult execute(AgentActionResponse action,
                                         AgentUserContext context,
                                         AgentActionExecuteRequest request) {
        String refId = "ticket-note-" + action.actionId();
        boolean replay = ensureTicketNote(action, context, refId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("externalRefId", refId);
        response.put("ticketNoteDraftSaved", true);
        response.put("idempotentReplay", replay);
        response.put("manualPublishRequired", true);
        response.put("customerId", action.customerId());
        response.put("waybillId", action.waybillId());
        response.put("comment", request.comment());
        return new ActionExecutionResult(targetSystem(), refId, response);
    }

    private boolean ensureTicketNote(AgentActionResponse action, AgentUserContext context, String noteId) {
        String existing = jdbcTemplate.query("""
                SELECT note_id FROM logistics_ticket_note
                WHERE tenant_id = ? AND action_id = ?
                LIMIT 1
                """, rs -> rs.next() ? rs.getString("note_id") : null, context.tenantId(), action.actionId());
        if (existing != null) {
            return true;
        }
        jdbcTemplate.update("""
                INSERT INTO logistics_ticket_note
                (tenant_id, note_id, action_id, customer_id, waybill_id, content, status, created_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, context.tenantId(), noteId, action.actionId(), action.customerId(), action.waybillId(),
                action.draftContent(), "DRAFT", context.userId(), Timestamp.from(Instant.now()));
        return false;
    }
}
