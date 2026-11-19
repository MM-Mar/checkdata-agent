package com.checkdata.agent.service.permission;

import com.checkdata.agent.common.datascope.DataScopeContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;

import java.util.Set;

/**
 * 数据权限规则：为单张表生成 WHERE 条件。返回 null 表示不注入。
 */
public interface PermissionRule {

    /**
     * 该规则接管的表名（小写纯名）。Registry 按它做精确路由，未命中的表走 DefaultPermissionRule。
     */
    Set<String> managedTables();

    /**
     * 为表构造权限条件，返回 null 表示不注入。
     */
    Expression buildCondition(Table table, DataScopeContext ctx);
}
