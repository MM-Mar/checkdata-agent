package com.checkdata.agent.domain.entities;

import com.checkdata.agent.domain.entities.SchemaCatalog.ColumnDesc;
import com.checkdata.agent.domain.entities.SchemaCatalog.FkDesc;
import com.checkdata.agent.domain.entities.SchemaCatalog.TableDesc;

import java.util.List;
import java.util.Map;

/**
 * 表/视图的完整内存视图
 *
 * @param name          表/视图名
 * @param desc          原始 TableDesc
 * @param view          是否视图
 * @param relatedTables 外键指向的其它表（去重排序）
 */
public record TableEntry(String name,
                         TableDesc desc,
                         boolean view,
                         List<String> relatedTables) {

    public Map<String, ColumnDesc> columns() {
        return desc.columns();
    }

    public List<FkDesc> foreignKeys() {
        return desc.foreignKeys();
    }

    public String description() {
        return desc.description() == null ? "" : desc.description();
    }
}
