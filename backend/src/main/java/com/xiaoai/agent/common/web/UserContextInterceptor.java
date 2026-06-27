package com.xiaoai.agent.common.web;

import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Component
public class UserContextInterceptor implements HandlerInterceptor {

    public static final String TENANT_ID_HEADER = "X-Tenant-Id";
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USERNAME_HEADER = "X-Username";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TENANT_ID_PARAMETER = "tenantId";
    public static final String USER_ID_PARAMETER = "userId";

    @Override
public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (!StringUtils.hasText(traceId)) {
            traceId = UUID.randomUUID().toString();
        }
        UserContextHolder.set(UserContext.builder()
                .tenantId(parseLong(firstText(request.getHeader(TENANT_ID_HEADER), request.getParameter(TENANT_ID_PARAMETER))))
                .userId(parseLong(firstText(request.getHeader(USER_ID_HEADER), request.getParameter(USER_ID_PARAMETER))))
                .username(request.getHeader(USERNAME_HEADER))
                .traceId(traceId)
                .build());
        response.setHeader(TRACE_ID_HEADER, traceId);
        return true;
    }

    @Override
public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContextHolder.clear();
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return Long.parseLong(value);
    }

    private String firstText(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }
}
