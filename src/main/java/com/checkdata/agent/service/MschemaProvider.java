package com.checkdata.agent.service;

import com.checkdata.agent.domain.entities.Mschema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * MSchema 实现——实时读取数据库元数据，输出 M-Schema 格式。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "data-agent.schema.provider", havingValue = "mschema", matchIfMissing = true)
public class MschemaProvider implements SchemaProvider {

    private final MschemaCacheService cache;

    public MschemaProvider(MschemaCacheService cache) {
        this.cache = cache;
    }

    @Override
    public String listTables() {
        Mschema schema = cache.get();
        if (schema.tables().isEmpty()) {
            return "schema 为空——可能是数据库无表或 MSchema 配置错误，请检查 exclude-tables 设置。";
        }
        return MschemaFormatter.formatTableList(schema);
    }

    @Override
    public String describeTables(List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return "tableNames 为空。请传入具体表名（逗号分隔），或先调 listTables 查看可用的表。";
        }

        Mschema schema = cache.get();
        List<String> found = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String name : tableNames) {
            String trimmed = name.trim();
            if (schema.tables().containsKey(trimmed)) {
                found.add(trimmed);
            } else if (!trimmed.isEmpty()) {
                missing.add(trimmed);
            }
        }

        if (found.isEmpty()) {
            return "未找到匹配的表："
                    + String.join(", ", missing)
                    + "。请确认表名拼写（建议先调 listTables 看准确表名）。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(MschemaFormatter.formatTables(schema, found));
        if (!missing.isEmpty()) {
            sb.append("\n\n⚠ 未找到的表：").append(String.join(", ", missing))
                    .append("（已跳过；建议调 listTables 核对准确表名）");
        }
        return sb.toString().trim();
    }
}
