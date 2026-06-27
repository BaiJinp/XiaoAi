package com.xiaoai.agent.common.context;

import com.xiaoai.agent.common.api.ErrorCode;
import com.xiaoai.agent.common.exception.BusinessException;

public final class UserContextHolder {

    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static void set(UserContext context) {
        HOLDER.set(context);
    }

    public static UserContext get() {
        return HOLDER.get();
    }

    public static Long getTenantId() {
        UserContext context = get();
        return context == null ? null : context.getTenantId();
    }

    public static Long requireTenantId() {
        Long tenantId = getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing tenant context");
        }
        return tenantId;
    }

    public static Long getUserId() {
        UserContext context = get();
        return context == null ? null : context.getUserId();
    }

    public static Long requireUserId() {
        Long userId = getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Missing user context");
        }
        return userId;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
