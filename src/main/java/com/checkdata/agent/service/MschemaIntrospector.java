package com.checkdata.agent.service;

import com.checkdata.agent.domain.entities.Mschema;
import com.checkdata.agent.domain.entities.Mschema.FkDef;
import com.checkdata.agent.domain.entities.Mschema.FieldDef;
import com.checkdata.agent.domain.entities.Mschema.TableDef;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 纯 Java 数据库 schema 自省器。
 * 用 JDBC DatabaseMetaData 读取表/列/主键/外键，用 JdbcTemplate 采集字符串列的示例值。
 */
@Slf4j
@Service
public class MschemaIntrospector {

    /**
     * 需要采集示例值的类型（仅字符串类，数值/时间/二进制不采）。
     */
    private static final Set<String> SAMPLE_VALUE_TYPES = Set.of(
            "VARCHAR", "CHAR", "ENUM", "SET", "TINYTEXT", "TEXT", "MEDIUMTEXT"
    );

    /**
     * 每列最多采集的 distinct 值数量。
     */
    private static final int MAX_EXAMPLE_VALUES = 5;

    /**
     * 示例值采集每列的超时（秒）。
     */
    private static final int EXAMPLE_TIMEOUT_SECONDS = 5;

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final SensitiveFilter sensitiveFilter;

    public MschemaIntrospector(DataSource dataSource, SensitiveFilter sensitiveFilter) {
        this.dataSource = dataSource;
        this.sensitiveFilter = sensitiveFilter;
        // 自包装避开 JdbcTemplate bean 歧义（容器里有 pgVectorJdbcTemplate 顶替主库自动配置）
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /**
     * 读取数据库完整 schema。
     *
     * @param excludeTables 需排除的表名（支持 * 通配符，为空则不过滤）
     */
    public Mschema readSchema(String catalog, String schema, List<String> excludeTables) {
        long t0 = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();

            // 若未指定 catalog，从连接 URL 中取当前数据库名（MySQL 必须指定，否则查全库）
            String effectiveCatalog = catalog != null ? catalog : conn.getCatalog();
            String dbName = effectiveCatalog;
            List<Pattern> excludePatterns = compilePatterns(excludeTables);

            Map<String, TableDef> tables = new LinkedHashMap<>();
            try (ResultSet rs = meta.getTables(effectiveCatalog, schema, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (isExcluded(tableName, excludePatterns)) {
                        continue;
                    }
                    String comment = rs.getString("REMARKS");
                    tables.put(tableName, new TableDef(
                            comment == null ? "" : comment.trim(),
                            new LinkedHashMap<>()));
                }
            }

            try (ResultSet rs = meta.getTables(effectiveCatalog, schema, "%", new String[]{"VIEW"})) {
                while (rs.next()) {
                    String viewName = rs.getString("TABLE_NAME");
                    if (isExcluded(viewName, excludePatterns)) {
                        continue;
                    }
                    String comment = rs.getString("REMARKS");
                    tables.put(viewName, new TableDef(
                            comment == null ? "" : comment.trim(),
                            new LinkedHashMap<>()));
                }
            }

            for (String tableName : tables.keySet()) {
                TableDef td = tables.get(tableName);
                Map<String, FieldDef> fields = td.fields();

                // 主键
                Set<String> pks = readPrimaryKeys(meta, effectiveCatalog, schema, tableName);

                // 列信息
                try (ResultSet rs = meta.getColumns(effectiveCatalog, schema, tableName, "%")) {
                    while (rs.next()) {
                        String colName = rs.getString("COLUMN_NAME");
                        String type = rs.getString("TYPE_NAME");
                        boolean nullable = "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE"));
                        String colComment = rs.getString("REMARKS");
                        boolean isPk = pks.contains(colName);

                        fields.put(colName, new FieldDef(
                                type,
                                isPk,
                                nullable,
                                colComment == null ? "" : colComment.trim(),
                                new ArrayList<>()));
                    }
                }
            }

            List<FkDef> fks = new ArrayList<>();
            for (String tableName : tables.keySet()) {
                try (ResultSet rs = meta.getImportedKeys(effectiveCatalog, schema, tableName)) {
                    while (rs.next()) {
                        String fkCol = rs.getString("FKCOLUMN_NAME");
                        String refTable = rs.getString("PKTABLE_NAME");
                        String refCol = rs.getString("PKCOLUMN_NAME");
                        if (fkCol != null && refTable != null
                                && !isExcluded(refTable, excludePatterns)) {
                            fks.add(new FkDef(tableName, fkCol, refTable, refCol));
                        }
                    }
                }
            }

            collectExamplesAsync(tables);

            log.info("[checkdata-agent] MschemaIntrospector 完成：{} 表, {} 外键, 耗时 {}ms",
                    tables.size(), fks.size(), System.currentTimeMillis() - t0);
            return new Mschema(dbName, tables, fks);

        } catch (SQLException e) {
            throw new RuntimeException("MSchema 自省失败：" + e.getMessage(), e);
        }
    }

    private void collectExamplesAsync(Map<String, TableDef> tables) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (var tableEntry : tables.entrySet()) {
            String tableName = tableEntry.getKey();
            for (var fieldEntry : tableEntry.getValue().fields().entrySet()) {
                String colName = fieldEntry.getKey();
                FieldDef fd = fieldEntry.getValue();
                if (sensitiveFilter.isSensitiveField(tableName, colName)) {
                    continue;
                }
                if (SAMPLE_VALUE_TYPES.contains(fd.type().toUpperCase())) {
                    futures.add(CompletableFuture.runAsync(() -> {
                        try {
                            List<String> values = fetchDistinct(tableName, colName);
                            fieldEntry.getValue().examples().clear();
                            fieldEntry.getValue().examples().addAll(values);
                        } catch (Exception e) {
                            // 采集失败不影响主流程
                            log.debug("示例值采集失败 {}.{}: {}", tableName, colName, e.getMessage());
                        }
                    }));
                }
            }
        }
        if (!futures.isEmpty()) {
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(EXAMPLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.debug("示例值采集超时或异常（已有部分结果）：{}", e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> fetchDistinct(String tableName, String colName) {
        String sql = String.format(
                "SELECT DISTINCT `%s` FROM `%s` WHERE `%s` IS NOT NULL LIMIT %d",
                colName, tableName, colName, MAX_EXAMPLE_VALUES);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        return rows.stream()
                .map(m -> m.get(colName))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private Set<String> readPrimaryKeys(DatabaseMetaData meta, String catalog,
                                        String schema, String tableName) throws SQLException {
        try (ResultSet rs = meta.getPrimaryKeys(catalog, schema, tableName)) {
            List<String> pks = new ArrayList<>();
            while (rs.next()) {
                pks.add(rs.getString("COLUMN_NAME"));
            }
            return Set.copyOf(pks);
        }
    }

    private List<Pattern> compilePatterns(List<String> excludeTables) {
        if (excludeTables == null || excludeTables.isEmpty()) {
            return List.of();
        }
        return excludeTables.stream()
                .map(s -> s.trim().replace("*", ".*"))
                .filter(s -> !s.isEmpty())
                .map(Pattern::compile)
                .toList();
    }

    private boolean isExcluded(String tableName, List<Pattern> patterns) {
        if (patterns.isEmpty()) {
            return false;
        }
        return patterns.stream().anyMatch(p -> p.matcher(tableName).matches());
    }
}