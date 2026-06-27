package com.xiaoai.agent.common.config;

import com.xiaoai.agent.common.web.UserContextInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WebMvcConfigTest {

    @Test
    void shouldAllowLocalFrontendCorsForApi() {
        WebMvcConfig config = new WebMvcConfig(new UserContextInterceptor());
        InspectableCorsRegistry registry = new InspectableCorsRegistry();

        config.addCorsMappings(registry);

        CorsConfiguration cors = registry.configurations().get("/api/**");
        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins()).contains("http://127.0.0.1:5173", "http://localhost:5173");
        assertThat(cors.getAllowedMethods()).contains("GET", "POST", "OPTIONS");
        assertThat(cors.getAllowedHeaders()).contains("*");
        assertThat(cors.getExposedHeaders()).contains(UserContextInterceptor.TRACE_ID_HEADER);
    }

    private static class InspectableCorsRegistry extends CorsRegistry {

        private Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }
}
