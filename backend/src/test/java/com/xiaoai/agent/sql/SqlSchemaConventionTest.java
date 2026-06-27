package com.xiaoai.agent.sql;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SqlSchemaConventionTest {

    private static final List<String> REQUIRED_COMMON_COLUMNS = List.of(
            "id",
            "bid",
            "created_by",
            "created_at",
            "updated_by",
            "updated_at",
            "deleted"
    );

    @Test
    void allCreateTablesShouldContainCommonColumns() throws Exception {
        Path sqlPath = Path.of("..", "docs", "sql", "20260604_企业数字员工平台.sql");
        String sql = Files.readString(sqlPath, StandardCharsets.UTF_8);
        Pattern tablePattern = Pattern.compile("CREATE TABLE\\s+([a-zA-Z_][\\w]*)\\s*\\(([\\s\\S]*?)\\);",
                Pattern.CASE_INSENSITIVE);
        Pattern columnPattern = Pattern.compile("^\\s*([a-zA-Z_][\\w]*)\\s+", Pattern.MULTILINE);
        Matcher tableMatcher = tablePattern.matcher(sql);
        List<String> violations = new ArrayList<>();
        int tableCount = 0;

        while (tableMatcher.find()) {
            tableCount++;
            String tableName = tableMatcher.group(1);
            String tableBody = tableMatcher.group(2);
            Matcher columnMatcher = columnPattern.matcher(tableBody);
            Set<String> columns = new java.util.HashSet<>();
            while (columnMatcher.find()) {
                columns.add(columnMatcher.group(1).toLowerCase(Locale.ROOT));
            }
            List<String> missingColumns = REQUIRED_COMMON_COLUMNS.stream()
                    .filter(column -> !columns.contains(column))
                    .toList();
            if (!missingColumns.isEmpty()) {
                violations.add(tableName + " missing " + missingColumns);
            }
        }

        assertThat(tableCount).isGreaterThan(0);
        assertThat(violations).isEmpty();
    }
}
