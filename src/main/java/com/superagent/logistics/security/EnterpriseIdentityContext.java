package com.superagent.logistics.security;

public final class EnterpriseIdentityContext {

    private static final ThreadLocal<AgentUserContext> CURRENT = new ThreadLocal<>();

    private EnterpriseIdentityContext() {
    }

    public static AgentUserContext current() {
        return CURRENT.get();
    }

    public static void set(AgentUserContext context) {
        CURRENT.set(context);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
