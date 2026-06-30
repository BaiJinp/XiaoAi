package com.xiaoai.agent.common.config;

import com.xiaoai.agent.common.web.UserContextInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class WebMvcConfigTest {

    @Test
    void shouldAllowLocalFrontendCorsForApi() throws Exception {
        WebMvcConfig config = new WebMvcConfig(new UserContextInterceptor());

        var corsFilter = config.corsFilter();
        assertThat(corsFilter).isNotNull();

        // Extract CorsConfigurationSource from CorsFilter via reflection
        Field sourceField = org.springframework.web.filter.CorsFilter.class.getDeclaredField("configSource");
        sourceField.setAccessible(true);
        CorsConfigurationSource source = (CorsConfigurationSource) sourceField.get(corsFilter);

        // Verify config is registered for /api/**
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/model-providers");
        CorsConfiguration cors = source.getCorsConfiguration(request);
        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOriginPatterns()).contains("http://localhost:*", "http://127.0.0.1:*");
        assertThat(cors.getAllowedMethods()).contains("GET", "POST", "PUT", "DELETE", "OPTIONS");
        assertThat(cors.getAllowedHeaders()).contains("*");
        assertThat(cors.getExposedHeaders()).contains(UserContextInterceptor.TRACE_ID_HEADER);
        assertThat(cors.getAllowCredentials()).isTrue();
    }
}
