package com.checkdata.agent.service;

import com.checkdata.agent.domain.entities.LookupResult;
import com.checkdata.agent.domain.entities.SchemaCatalog.FkDesc;
import com.checkdata.agent.domain.entities.SchemaCatalog.GlossaryDesc;
import com.checkdata.agent.domain.entities.SchemaCatalog.Root;
import com.checkdata.agent.domain.entities.SchemaCatalog.TableDesc;
import com.checkdata.agent.domain.entities.TableEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * schema 字典加载服务
 */
@Slf4j
@Service
public class SchemaCatalogService {

    /**
     * schema 字典在 classpath 下的路径。
     */
    private static final String SCHEMA_YAML = "schema/checkdata_agent.yml";

    private volatile Root catalog;

    @PostConstruct
    public void load() {
        long t0 = System.currentTimeMillis();
        try (InputStream in = new ClassPathResource(SCHEMA_YAML).getInputStream()) {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            catalog = mapper.readValue(in, Root.class);
            log.info("[checkdata-agent] schema 字典加载完成：{} 表 + {} 视图 + {} 术语，耗时 {}ms",
                    catalog.tables().size(), catalog.views().size(), catalog.glossary().size(),
                    System.currentTimeMillis() - t0);
        } catch (Exception e) {
            catalog = new Root(Map.of(), Map.of(), List.of());
            throw new IllegalStateException(
                    "加载 schema/glossary 字典失败：" + SCHEMA_YAML + "，data 工具将无法工作", e);
        }
    }

    /**
     * 列出全部表和视图（含描述 + 关联表）。
     */
    public List<TableEntry> listAll() {
        List<TableEntry> out = new ArrayList<>();
        catalog.tables().forEach((name, desc) ->
                out.add(new TableEntry(name, desc, false, relatedTablesOf(desc))));
        catalog.views().forEach((name, desc) ->
                out.add(new TableEntry(name, desc, true, relatedTablesOf(desc))));
        return out;
    }

    /**
     * 按表名查详情；先查 tables 再查 views，找不到返回 null。
     */
    public TableEntry find(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        TableDesc t = catalog.tables().get(trimmed);
        if (t != null) {
            return new TableEntry(trimmed, t, false, relatedTablesOf(t));
        }
        TableDesc v = catalog.views().get(trimmed);
        if (v != null) {
            return new TableEntry(trimmed, v, true, relatedTablesOf(v));
        }
        return null;
    }

    /**
     * 批量查详情；未找到的表名出现在 TableEntry#missing() 里供调用方提示 LLM。
     */
    public LookupResult findMany(List<String> names) {
        if (names == null || names.isEmpty()) {
            return new LookupResult(List.of(), List.of());
        }
        List<TableEntry> found = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String n : names) {
            String trimmed = n == null ? "" : n.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            TableEntry e = find(trimmed);
            if (e != null) {
                found.add(e);
            } else {
                missing.add(trimmed);
            }
        }
        return new LookupResult(found, missing);
    }

    /**
     * 该表的外键目标表名（去重 + 排序）。
     */
    private List<String> relatedTablesOf(TableDesc desc) {
        if (desc == null || desc.foreignKeys().isEmpty()) {
            return Collections.emptyList();
        }
        return desc.foreignKeys().stream()
                .map(FkDesc::refTable)
                .filter(s -> s != null && !s.isEmpty())
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * 全部业务术语（按 YAML 配置顺序），供 LookupGlossaryTool 拼进 tool description。
     */
    public List<GlossaryDesc> glossary() {
        return catalog.glossary();
    }
}
