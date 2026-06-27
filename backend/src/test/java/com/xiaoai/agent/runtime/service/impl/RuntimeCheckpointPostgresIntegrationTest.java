package com.xiaoai.agent.runtime.service.impl;

import com.xiaoai.agent.common.context.UserContext;
import com.xiaoai.agent.common.context.UserContextHolder;
import com.xiaoai.agent.runtime.entity.RuntimeCheckpoint;
import com.xiaoai.agent.runtime.service.RuntimeCheckpointService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Sql(statements = {
        "DROP TABLE IF EXISTS runtime_checkpoint",
        "CREATE TABLE runtime_checkpoint (" +
                "id BIGSERIAL PRIMARY KEY," +
                "bid VARCHAR(64)," +
                "tenant_id BIGINT NOT NULL," +
                "task_id BIGINT NOT NULL," +
                "run_id BIGINT NOT NULL," +
                "checkpoint_type VARCHAR(64) NOT NULL," +
                "checkpoint_status VARCHAR(32) NOT NULL DEFAULT 'suspended'," +
                "approval_request_id BIGINT," +
                "payload_json JSONB NOT NULL DEFAULT '{}'::jsonb," +
                "resume_payload_json JSONB NOT NULL DEFAULT '{}'::jsonb," +
                "created_by BIGINT," +
                "created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()," +
                "updated_by BIGINT," +
                "updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()," +
                "deleted BOOLEAN NOT NULL DEFAULT FALSE," +
                "CONSTRAINT uk_runtime_checkpoint_run_type UNIQUE (tenant_id, run_id, checkpoint_type, checkpoint_status)" +
                ")"
})
class RuntimeCheckpointPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("enterprise_agent_test")
            .withUsername("postgres")
            .withPassword("postgres");

    @Autowired
    private RuntimeCheckpointService runtimeCheckpointService;

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
                .tenantId(100L)
                .userId(200L)
                .traceId("trace-checkpoint-it")
                .build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void claimApprovedCheckpointShouldBeAtomicAgainstRealPostgresState() {
        RuntimeCheckpoint checkpoint = new RuntimeCheckpoint();
        checkpoint.setTenantId(100L);
        checkpoint.setTaskId(10L);
        checkpoint.setRunId(99L);
        checkpoint.setCheckpointType("tool_call");
        checkpoint.setCheckpointStatus("approved");
        checkpoint.setApprovalRequestId(88L);
        checkpoint.setPayloadJson("{\"toolId\":1}");
        checkpoint.setResumePayloadJson("{}");
        runtimeCheckpointService.save(checkpoint);

        boolean firstClaimed = runtimeCheckpointService.claimApprovedCheckpoint(100L, 99L, 88L);
        boolean secondClaimed = runtimeCheckpointService.claimApprovedCheckpoint(100L, 99L, 88L);
        RuntimeCheckpoint stored = runtimeCheckpointService.getById(checkpoint.getId());

        assertThat(firstClaimed).isTrue();
        assertThat(secondClaimed).isFalse();
        assertThat(stored.getCheckpointStatus()).isEqualTo("resuming");
        assertThat(stored.getTenantId()).isEqualTo(100L);
        assertThat(stored.getRunId()).isEqualTo(99L);
        assertThat(stored.getApprovalRequestId()).isEqualTo(88L);
    }

    @Test
    void claimApprovedCheckpointShouldRejectCrossTenantOrWrongApprovalAgainstRealPostgresState() {
        RuntimeCheckpoint checkpoint = new RuntimeCheckpoint();
        checkpoint.setTenantId(100L);
        checkpoint.setTaskId(10L);
        checkpoint.setRunId(99L);
        checkpoint.setCheckpointType("tool_call");
        checkpoint.setCheckpointStatus("approved");
        checkpoint.setApprovalRequestId(88L);
        checkpoint.setPayloadJson("{\"toolId\":1}");
        checkpoint.setResumePayloadJson("{}");
        runtimeCheckpointService.save(checkpoint);

        boolean crossTenantClaimed = runtimeCheckpointService.claimApprovedCheckpoint(101L, 99L, 88L);
        boolean wrongApprovalClaimed = runtimeCheckpointService.claimApprovedCheckpoint(100L, 99L, 89L);
        RuntimeCheckpoint stored = runtimeCheckpointService.getById(checkpoint.getId());

        assertThat(crossTenantClaimed).isFalse();
        assertThat(wrongApprovalClaimed).isFalse();
        assertThat(stored.getCheckpointStatus()).isEqualTo("approved");
    }
}
