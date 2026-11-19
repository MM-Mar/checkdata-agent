package com.checkdata.agent.service;

import com.checkdata.agent.domain.entities.LookupResult;
import com.checkdata.agent.domain.entities.SchemaCatalog.ColumnDesc;
import com.checkdata.agent.domain.entities.SchemaCatalog.FkDesc;
import com.checkdata.agent.domain.entities.TableEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * YAML 实现——读取 classpath:schema/checkdata_agent.yml，输出与传统格式一致。
 * 仅在 provider=yml 时激活。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "data-agent.schema.provider", havingValue = "yml")
public class YamlSchemaProvider implements SchemaProvider {

    private final SchemaCatalogService catalog;

    public YamlSchemaProvider(SchemaCatalogService catalog) {
        this.catalog = catalog;
    }

    @Override
    public String listTables() {
        List<TableEntry> all = catalog.listAll();
        if (all.isEmpty()) {
            return "schema 字典为空——可能是配置文件未加载，请联系运维检查 schema/checkdata_agent.yml。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("当前数据库共 ").append(all.size()).append(" 个表/视图（YAML 静态字典）：\n\n");
        for (TableEntry e : all) {
            sb.append("- ").append(e.name());
            if (e.view()) {
                sb.append(" [视图]");
            }
            if (!e.description().isEmpty()) {
                sb.append("（").append(e.description()).append("）");
            }
            if (!e.relatedTables().isEmpty()) {
                sb.append(" → 关联: ").append(String.join(", ", e.relatedTables()));
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    @Override
    public String describeTables(List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return "tableNames 为空。请传入具体表名（逗号分隔），或先调 listTables 查看可用的表。";
        }
        List<String> names = tableNames;
        if (tableNames.size() == 1 && tableNames.get(0).contains(",")) {
            names = Arrays.stream(tableNames.get(0).split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }

        LookupResult result = catalog.findMany(names);
        if (result.found().isEmpty()) {
            return "未找到匹配的表："
                    + String.join(", ", result.missing())
                    + "。请确认表名拼写（建议先调 listTables 看准确表名）。";
        }

        StringBuilder sb = new StringBuilder();
        for (TableEntry e : result.found()) {
            sb.append(formatTableLegacy(e)).append("\n\n");
        }
        if (!result.missing().isEmpty()) {
            sb.append("⚠ 未找到的表：").append(String.join(", ", result.missing()))
                    .append("（已跳过；建议调 listTables 核对准确表名）");
        }
        return sb.toString().trim();
    }

    /**
     * 传统格式输出（与现有的 DescribeTablesTool 完全一致）。
     */
    private String formatTableLegacy(TableEntry e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.view() ? "视图 " : "表 ").append(e.name());
        if (!e.description().isEmpty()) {
            sb.append("（").append(e.description()).append("）");
        }
        sb.append("：\n\n字段：\n");
        for (Map.Entry<String, ColumnDesc> entry : e.columns().entrySet()) {
            String colName = entry.getKey();
            ColumnDesc c = entry.getValue();
            sb.append("  - ").append(colName);
            if (c != null) {
                if (c.type() != null && !c.type().isEmpty()) {
                    sb.append(' ').append(c.type());
                }
                if (c.key() != null && !c.key().isEmpty()) {
                    sb.append(", ").append(c.key());
                }
                if (!c.isNullable()) {
                    sb.append(", NOT NULL");
                }
                if (c.comment() != null && !c.comment().isEmpty()) {
                    sb.append(" -- ").append(c.comment());
                }
            }
            sb.append('\n');
        }
        if (!e.foreignKeys().isEmpty()) {
            sb.append("\n外键：\n");
            for (FkDesc fk : e.foreignKeys()) {
                sb.append("  - ").append(fk.column())
                        .append(" → ").append(fk.refTable()).append("(").append(fk.refColumn()).append(")\n");
            }
        }
        return sb.toString().trim();
    }
}
