package com.superagent.logistics.security;

import org.springframework.stereotype.Service;

@Service
public class AgentPermissionService {

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
