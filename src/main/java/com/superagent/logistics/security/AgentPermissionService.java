package com.superagent.logistics.security;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class AgentPermissionService {

    public static final String CHAT_USE = "CHAT_USE";
    public static final String OPS_VIEW = "OPS_VIEW";
    public static final String AUDIT_VIEW = "AUDIT_VIEW";
    public static final String ACTION_MANAGE = "ACTION_MANAGE";
    public static final String KNOWLEDGE_MANAGE = "KNOWLEDGE_MANAGE";
    public static final String QUALITY_MANAGE = "QUALITY_MANAGE";
    public static final String EVAL_MANAGE = "EVAL_MANAGE";

    public List<String> permissions(AgentUserContext context) {
        Set<String> permissions = new LinkedHashSet<>();
        if (context.hasAnyRole("CUSTOMER_SERVICE", "SALES", "OPERATIONS", "OPS_MANAGER", "ADMIN")) {
            permissions.add(CHAT_USE);
            permissions.add(AUDIT_VIEW);
        }
        if (context.hasAnyRole("OPERATIONS", "OPS_MANAGER", "ADMIN")) {
            permissions.add(OPS_VIEW);
            permissions.add(ACTION_MANAGE);
            permissions.add(KNOWLEDGE_MANAGE);
            permissions.add(QUALITY_MANAGE);
            permissions.add(EVAL_MANAGE);
        }
        List<String> result = new ArrayList<>(permissions);
        result.sort(String::compareTo);
        return result;
    }

    public void checkBusinessReadable(AgentUserContext context) {
        if (!context.hasAnyRole("CUSTOMER_SERVICE", "OPERATIONS", "OPS_MANAGER", "SALES", "ADMIN")) {
            throw new AccessDeniedException("当前用户没有物流业务数据查询权限");
        }
    }

    public void checkCustomerReadable(AgentUserContext context, String customerId) {
        checkBusinessReadable(context);
        if (customerId == null || customerId.isBlank()) {
            throw new AccessDeniedException("缺少客户编号");
        }
        if (context.hasAnyRole("ADMIN", "OPS_MANAGER", "CUSTOMER_SERVICE")) {
            return;
        }
        if (context.hasAnyRole("SALES") && customerId.startsWith("C0")) {
            return;
        }
        throw new AccessDeniedException("当前用户无权访问客户 " + customerId);
    }

    public void checkWaybillReadable(AgentUserContext context, String waybillId) {
        checkBusinessReadable(context);
        if (waybillId == null || waybillId.isBlank()) {
            throw new AccessDeniedException("缺少运单号");
        }
    }

    public boolean canReadKnowledge(AgentUserContext context, String aclRoles) {
        if (context.hasAnyRole("ADMIN")) {
            return true;
        }
        if (aclRoles == null || aclRoles.isBlank() || "PUBLIC".equalsIgnoreCase(aclRoles)) {
            return true;
        }
        for (String role : aclRoles.split(",")) {
            if (context.roles().contains(role.trim())) {
                return true;
            }
        }
        return false;
    }
}
