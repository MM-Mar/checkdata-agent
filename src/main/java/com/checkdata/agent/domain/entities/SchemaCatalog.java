package com.checkdata.agent.domain.entities;

import java.util.List;
import java.util.Map;

/**
 * schema 字典的内存模型（由 checkdata_agent.yml 加载）。record 嵌套聚合在一个文件，与 YAML 结构一一对应。
 */
public final class SchemaCatalog {

    private SchemaCatalog() {
    }

    /**
     * YAML 根。
     */
    public record Root(Map<String, TableDesc> tables, Map<String, TableDesc> views,
                       List<GlossaryDesc> glossary) {
        public Map<String, TableDesc> tables() {
            return tables == null ? Map.of() : tables;
        }

        public Map<String, TableDesc> views() {
            return views == null ? Map.of() : views;
        }

        public List<GlossaryDesc> glossary() {
            return glossary == null ? List.of() : glossary;
        }
    }

    /**
     * 表/视图统一形态（视图通常没有 foreignKeys）。
     */
    public record TableDesc(String description,
                            Map<String, ColumnDesc> columns,
                            List<FkDesc> foreignKeys) {
        public Map<String, ColumnDesc> columns() {
            return columns == null ? Map.of() : columns;
        }

        public List<FkDesc> foreignKeys() {
            return foreignKeys == null ? List.of() : foreignKeys;
        }
    }

    /**
     * 字段元信息。nullable 用 Boolean：YAML 可省略，为 null 时默认按可空处理。
     */
    public record ColumnDesc(String type, String comment, Boolean nullable, String key) {
        public boolean isNullable() {
            return nullable == null || nullable;
        }
    }

    /**
     * 外键关系：column → refTable（refColumn）。
     */
    public record FkDesc(String column, String refTable, String refColumn) {
    }

    /**
     * 业务术语标准口径。
     */
    public record GlossaryDesc(String term, String description, String sqlFragment,
                               List<String> synonyms) {
        public List<String> synonyms() {
            return synonyms == null ? List.of() : synonyms;
        }
    }
}
