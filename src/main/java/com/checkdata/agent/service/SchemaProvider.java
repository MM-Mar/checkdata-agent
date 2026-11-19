package com.checkdata.agent.service;

import java.util.List;

/**
 * Schema 数据源接口。
 */
public interface SchemaProvider {

    /**
     * 列出全部表/视图（含描述 + 关联表）。
     */
    String listTables();

    /**
     * 查看指定表的字段详情。
     *
     * @param tableNames 表名列表（逗号分隔或 List）
     */
    String describeTables(List<String> tableNames);
}