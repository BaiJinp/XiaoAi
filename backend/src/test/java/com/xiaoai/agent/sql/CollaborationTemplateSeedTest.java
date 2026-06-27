package com.xiaoai.agent.sql;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class CollaborationTemplateSeedTest {

    @Test
    void softwareDeliverySeedShouldUseOnlyGenericCollaborationTables() throws Exception {
        Path seedPath = Path.of("..", "docs", "sql", "20260611_通用多Agent协作_场景种子.sql");
        String sql = Files.readString(seedPath, StandardCharsets.UTF_8);
        String normalized = sql.toLowerCase(Locale.ROOT);

        assertThat(normalized).contains("insert into agent_role");
        assertThat(normalized).contains("insert into artifact_type");
        assertThat(normalized).contains("insert into collaboration_template");
        assertThat(normalized).contains("software_requirement_to_delivery");
        assertThat(normalized).contains("software_product_manager");
        assertThat(normalized).contains("requirement_analysis");
        assertThat(normalized).contains("requirement_confirmed");
        assertThat(normalized).contains("\"createtask\": true");
        assertThat(normalized).contains("\"autostarttask\": true");
        assertThat(normalized).contains("technical_design");
        assertThat(normalized).contains("backend_implementation");
        assertThat(normalized).contains("frontend_implementation");
        assertThat(normalized).contains("testing");
        assertThat(normalized).contains("review_and_delivery");
        assertThat(normalized).contains("delivery_confirmed");
        assertThat(normalized).doesNotContain("create table software_");
        assertThat(normalized).doesNotContain("software_product_manager enum");
        assertThat(normalized).doesNotContain("software_developer enum");
    }

    @Test
    void platformSqlShouldContainCollaborationRoleBindingTable() throws Exception {
        String sql = Files.list(Path.of("..", "docs", "sql"))
                .filter(path -> path.getFileName().toString().endsWith(".sql"))
                .map(path -> {
                    try {
                        return Files.readString(path, StandardCharsets.UTF_8);
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .reduce("", String::concat);
        String normalized = sql.toLowerCase(Locale.ROOT);

        assertThat(normalized).contains("create table collaboration_role_binding");
        assertThat(normalized).contains("uk_collaboration_role_binding");
        assertThat(normalized).contains("idx_collaboration_role_binding_template");
        assertThat(normalized).contains("idx_collaboration_role_binding_agent");
    }
}
