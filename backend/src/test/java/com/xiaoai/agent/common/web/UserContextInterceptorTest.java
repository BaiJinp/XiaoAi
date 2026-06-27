package com.xiaoai.agent.common.web;

import com.xiaoai.agent.common.context.UserContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class UserContextInterceptorTest {

    private final UserContextInterceptor interceptor = new UserContextInterceptor();

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void preHandleShouldReadTenantAndUserFromHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(UserContextInterceptor.TENANT_ID_HEADER, "100");
        request.addHeader(UserContextInterceptor.USER_ID_HEADER, "200");

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(UserContextHolder.requireTenantId()).isEqualTo(100L);
        assertThat(UserContextHolder.requireUserId()).isEqualTo(200L);
    }

    @Test
    void preHandleShouldReadTenantAndUserFromQueryForSse() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(UserContextInterceptor.TENANT_ID_PARAMETER, "100");
        request.setParameter(UserContextInterceptor.USER_ID_PARAMETER, "200");

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(UserContextHolder.requireTenantId()).isEqualTo(100L);
        assertThat(UserContextHolder.requireUserId()).isEqualTo(200L);
    }
}
